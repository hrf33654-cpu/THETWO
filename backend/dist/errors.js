export class ApiError extends Error {
    errorCode;
    statusCode;
    constructor(errorCode, message, statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }
}
export function badRequest(errorCode, message) {
    throw new ApiError(errorCode, message, 400);
}
export function unauthorized(message = "未授权") {
    throw new ApiError("UNAUTHORIZED", message, 401);
}
export function notFound(errorCode, message) {
    throw new ApiError(errorCode, message, 404);
}
export function badGateway(errorCode, message) {
    throw new ApiError(errorCode, message, 502);
}
export function serviceUnavailable(errorCode, message) {
    throw new ApiError(errorCode, message, 503);
}
export function gatewayTimeout(errorCode, message) {
    throw new ApiError(errorCode, message, 504);
}
