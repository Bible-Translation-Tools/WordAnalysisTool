export type WordsParams = {
  batchId: string;
  words: WordRequest[];
};

export type WordRequest = {
  word: string;
  correct: boolean;
};

export type BatchRequest = {
  batchId: string;
  language: string;
  words: string[];
  models: string[];
};

export type Batch = {
  id: string;
  ietf_code: string;
  resource_type: string;
  details: BatchDetails;
  creator: PublicUser;
};

export type BatchProgress = {
  correct: number;
  incorrect: number;
  name: number;
  review_needed: number;
  reviewed: number;
  completed: number;
  total: number;
};

export type BatchDetails = {
  status: string;
  error: BatchError | null;
  progress: BatchProgress;
  output: WordResponse[];
};

export type WordResponse = {
  word: string;
  ref: string;
  text?: string;
  correct: boolean | null;
  results: ModelResponse[];
};

export type ModelResponse = {
  model: string;
  status: number;
};

export type ChatResponse = {
  word: string;
  status: number;
};

export type BatchError = {
  message: string;
  prompt: string | null;
  model: string | null;
  response: string | null;
};

export type PublicUser = {
  username: string;
};

export enum BatchStatus {
  QUEUED = "queued",
  RUNNING = "running",
  COMPLETE = "complete",
  ERRORED = "errored",
}

export type ModelResult = {
  model: string;
  results: ChatResponse[];
  retries: number;
};

export type SplitBatchJson = {
  left: string;
  right: string;
};

export type WordData = {
  word: string;
  ref: string;
};
