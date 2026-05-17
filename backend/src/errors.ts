export class ApiError extends Error {
  constructor(
    public readonly errorCode: string,
    message: string,
    public readonly statusCode: number,
    public readonly details: Record<string, unknown> | null = null,
  ) {
    super(message);
  }
}

export function badRequest(
  errorCode: string,
  message: string,
  details?: Record<string, unknown>,
): never {
  throw new ApiError(errorCode, message, 400, details ?? null);
}

export function unauthorized(message = "未授权"): never {
  throw new ApiError("UNAUTHORIZED", message, 401);
}

export function notFound(
  errorCode: string,
  message: string,
  details?: Record<string, unknown>,
): never {
  throw new ApiError(errorCode, message, 404, details ?? null);
}

export function badGateway(
  errorCode: string,
  message: string,
  details?: Record<string, unknown>,
): never {
  throw new ApiError(errorCode, message, 502, details ?? null);
}

export function serviceUnavailable(
  errorCode: string,
  message: string,
  details?: Record<string, unknown>,
): never {
  throw new ApiError(errorCode, message, 503, details ?? null);
}

export function gatewayTimeout(
  errorCode: string,
  message: string,
  details?: Record<string, unknown>,
): never {
  throw new ApiError(errorCode, message, 504, details ?? null);
}
