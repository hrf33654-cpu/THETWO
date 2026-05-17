export type ApiSuccess<T> = {
  success: true;
  data: T;
  errorCode: null;
  message: string;
  details?: null;
};

export type ApiFailure = {
  success: false;
  data: null;
  errorCode: string;
  message: string;
  details?: Record<string, unknown> | null;
};

export type ApiResponse<T> = ApiSuccess<T> | ApiFailure;

export type RequestCodeResponse = {
  email: string;
  devCode: string | null;
  deliveryMode: "DEV" | "SMTP";
};

export type UserRecord = {
  id: string;
  email: string;
  createdAt: string;
};

export type SessionRecord = {
  userId: string;
  email: string;
  sessionToken: string;
  profileCompleted: boolean;
};

export type CompanionProfileRecord = {
  nickname: string;
  tone: string;
  personalityTags: string[];
  interestTags: string[];
};

export type ChatMode = "NORMAL" | "RESTRICTED";

export type ChatMessageRecord = {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  mode: ChatMode;
  clientMessageId: string | null;
  timestamp: string;
};

export type RecentCaptureRecord = {
  title: string;
  summary: string;
  storageLocation: string;
  updatedAt: string;
};

export type ChatSummaryRecord = {
  summary: string;
  sourceMessageCount: number;
  updatedAt: string;
};

export type MemoryStateRecord = {
  memoryNote: string;
  updatedAt: string;
};

export type SafetyStateRecord = {
  mode: ChatMode;
  reason: string;
  remainingUserTurns: number;
  updatedAt: string;
};
