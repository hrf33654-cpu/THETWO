import dns from "node:dns/promises";
import net from "node:net";
import tls from "node:tls";
import { config } from "./config.js";
import { gatewayTimeout, serviceUnavailable } from "./errors.js";

type MailSendResult = {
  deliveryMode: "DEV" | "SMTP";
};

export async function sendLoginCodeEmail(input: {
  email: string;
  code: string;
}): Promise<MailSendResult> {
  if (config.emailMode !== "smtp") {
    return { deliveryMode: "DEV" };
  }

  validateSmtpConfig();
  try {
    await sendSmtpMessage({
      to: input.email,
      subject: "THETWO 登录验证码",
      text: [
        "你的 THETWO 登录验证码如下：",
        "",
        input.code,
        "",
        `验证码有效期：${config.authCodeTtlMinutes} 分钟。`,
      ].join("\r\n"),
    });
  } catch (error) {
    if (error instanceof Error && error.message === "SMTP_TIMEOUT") {
      gatewayTimeout("EMAIL_TIMEOUT", "邮件服务连接超时，请稍后重试");
    }
    throw error;
  }
  return { deliveryMode: "SMTP" };
}

function validateSmtpConfig(): void {
  if (!config.smtpHost || !config.smtpUser || !config.smtpPass || !config.smtpFrom) {
    serviceUnavailable("EMAIL_NOT_CONFIGURED", "邮件服务尚未配置完成");
  }
}

async function sendSmtpMessage(input: {
  to: string;
  subject: string;
  text: string;
}): Promise<void> {
  const endpoint = await resolveSmtpEndpoint();
  const socket = await openSocket(endpoint);
  const reader = createLineReader(socket);

  await expectCode(await reader.readResponse(), [220]);
  await sendCommand(socket, reader, `EHLO thetwo.local`, [250]);

  if (config.smtpPort === 587) {
    await sendCommand(socket, reader, "STARTTLS", [220]);
    const securedSocket = tls.connect({
      socket,
      servername: endpoint.servername,
    });
    secureSocketWithTimeout(securedSocket);
    await awaitSocketReady(securedSocket, "secureConnect");
    await sendCommand(securedSocket, createLineReader(securedSocket), `EHLO thetwo.local`, [250]);
    await authenticateAndSend(securedSocket, createLineReader(securedSocket), input);
    return;
  }

  await authenticateAndSend(socket, reader, input);
}

async function authenticateAndSend(
  socket: net.Socket | tls.TLSSocket,
  reader: ReturnType<typeof createLineReader>,
  input: { to: string; subject: string; text: string },
): Promise<void> {
  await sendCommand(socket, reader, "AUTH LOGIN", [334]);
  await sendCommand(socket, reader, Buffer.from(config.smtpUser).toString("base64"), [334]);
  await sendCommand(socket, reader, Buffer.from(config.smtpPass).toString("base64"), [235]);
  await sendCommand(socket, reader, `MAIL FROM:<${config.smtpFrom}>`, [250]);
  await sendCommand(socket, reader, `RCPT TO:<${input.to}>`, [250, 251]);
  await sendCommand(socket, reader, "DATA", [354]);
  const body = [
    `From: THETWO <${config.smtpFrom}>`,
    `To: <${input.to}>`,
    `Subject: ${input.subject}`,
    "Content-Type: text/plain; charset=UTF-8",
    "",
    input.text,
    ".",
  ].join("\r\n");
  await sendCommand(socket, reader, body, [250]);
  await sendCommand(socket, reader, "QUIT", [221]);
}

async function resolveSmtpEndpoint(): Promise<{ connectHost: string; servername: string }> {
  if (net.isIP(config.smtpHost)) {
    return {
      connectHost: config.smtpHost,
      servername: config.smtpHost,
    };
  }

  try {
    const resolved = await dns.lookup(config.smtpHost, { family: 4 });
    return {
      connectHost: resolved.address,
      servername: config.smtpHost,
    };
  } catch {
    return {
      connectHost: config.smtpHost,
      servername: config.smtpHost,
    };
  }
}

function openSocket(endpoint: { connectHost: string; servername: string }): Promise<net.Socket | tls.TLSSocket> {
  return new Promise((resolve, reject) => {
    const socket = config.smtpPort === 465
      ? tls.connect({ host: endpoint.connectHost, port: config.smtpPort, servername: endpoint.servername })
      : net.createConnection({ host: endpoint.connectHost, port: config.smtpPort, family: 4 });
    secureSocketWithTimeout(socket);
    socket.once(config.smtpPort === 465 ? "secureConnect" : "connect", () => resolve(socket));
    socket.once("error", reject);
  });
}

function secureSocketWithTimeout(socket: net.Socket | tls.TLSSocket): void {
  socket.setTimeout(config.smtpTimeoutMs, () => {
    socket.destroy(new Error("SMTP_TIMEOUT"));
  });
}

function awaitSocketReady(
  socket: net.Socket | tls.TLSSocket,
  event: "secureConnect",
): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    socket.once(event, resolve);
    socket.once("error", reject);
  });
}

function createLineReader(socket: net.Socket | tls.TLSSocket) {
  let buffer = "";
  const pending: Array<{
    resolve: (value: string) => void;
    reject: (error: Error) => void;
  }> = [];

  const flushWithError = (error: Error) => {
    while (pending.length > 0) {
      pending.shift()?.reject(error);
    }
  };

  socket.on("data", (chunk) => {
    buffer += chunk.toString("utf8");
    while (buffer.includes("\n") && pending.length > 0) {
      const lines = buffer.split(/\r?\n/);
      const consumed: string[] = [];
      while (lines.length > 1) {
        const line = lines.shift() ?? "";
        consumed.push(line);
        if (/^\d{3} /.test(line)) {
          buffer = lines.join("\n");
          const next = pending.shift();
          next?.resolve(consumed.join("\n"));
          break;
        }
      }
    }
  });

  socket.on("error", (error) => {
    flushWithError(error instanceof Error ? error : new Error(String(error)));
  });

  socket.on("close", () => {
    flushWithError(new Error("SMTP_CONNECTION_CLOSED"));
  });

  return {
    readResponse(): Promise<string> {
      return new Promise((resolve, reject) => pending.push({ resolve, reject }));
    },
  };
}

async function sendCommand(
  socket: net.Socket | tls.TLSSocket,
  reader: ReturnType<typeof createLineReader>,
  command: string,
  expectedCodes: number[],
): Promise<void> {
  socket.write(`${command}\r\n`);
  await expectCode(await reader.readResponse(), expectedCodes);
}

async function expectCode(response: string, expectedCodes: number[]): Promise<void> {
  const code = Number(response.slice(0, 3));
  if (!expectedCodes.includes(code)) {
    serviceUnavailable("EMAIL_SEND_FAILED", `邮件发送失败：${response}`);
  }
}
