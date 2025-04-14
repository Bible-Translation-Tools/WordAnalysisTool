export type WordsParams = {
  batchId: string;
  words: WordsRequest[];
};

export type WordsRequest = {
  prompt: string;
  models: any[];
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
};

export type BatchProgress = {
  completed: number;
  total: number;
};

export type BatchDetails = {
  status: string;
  error: string | null;
  progress: BatchProgress;
  output: WordResponse[];
};

export type WordResponse = {
  word: string;
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

export enum BatchStatus {
  QUEUED = "queued",
  RUNNING = "running",
  COMPLETE = "complete",
  ERRORED = "errored",
}
