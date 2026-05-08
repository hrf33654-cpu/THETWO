export class ApiError extends Error {
  constructor(
    public readonly errorCode: string,
    message: string,
    public readonly statusCode: number,
  ) {
    super(message);
  }
}

export function badRequest(errorCode: string, message: string): never {
  throw new ApiError(errorCode, message, 400);
}

export function unauthorized(message = "未授权"): never {
  throw new ApiError("UNAUTHORIZED", message, 401);
}

export function notFound(errorCode: string, message: string): never {
  throw new ApiError(errorCode, message, 404);
}

export function badGateway(errorCode: string, message: string): never {
  throw new ApiError(errorCode, message, 502);
}

export function serviceUnavailable(errorCode: string, message: string): never {
  throw new ApiError(errorCode, message, 503);
}

export function gatewayTimeout(errorCode: string, message: string): never {
  throw new ApiError(errorCode, message, 504);
}
