export type WordsParams = {
  batchId: string;
  words: WordRequest[];
};

export type WordRequest = {
  id: string;
  prompt: string;
  models: any[];
};

export type BatchRequest = {
  batchId: string;
  request: WordRequest;
};

export type Batch = {
  id: string;
  ietf_code: string;
  resource_type: string;
  details: BatchDetails;
};

export type BatchProgress = {
  completed: number;
  failed: number;
  total: number;
};

export type BatchDetails = {
  status: string;
  error: string | null;
  progress: BatchProgress;
  output: WordResponse[] | null;
};

export type WordResponse = {
  id: string;
  errored: boolean;
  last_error: string | null;
  results: ModelResponse[] | null;
};

export type ModelResponse = {
  model: string;
  result: string | null;
};

export enum BatchStatus {
  QUEUED = "queued",
  RUNNING = "running",
  COMPLETE = "complete",
}

export type BatchEntity = {
  id: string;
  total: number;
};

export type WordEntity = {
  word: string;
  result: string | null;
  errored: boolean;
  last_error: string | null;
  created_at: string;
};
