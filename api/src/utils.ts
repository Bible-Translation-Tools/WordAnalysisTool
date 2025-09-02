import { BatchError, SplitBatchJson } from "./types";

export const isAdmin = (username: string, env: CloudflareBindings) => {
  const admins = env.WAT_ADMINS.split(",");
  return admins.includes(username);
};

export const chunkArray = (array: any[], size: number) => {
  const arr = [];
  for (var i = 0; i < array.length; i += size) {
    arr.push(array.slice(i, i + size));
  }
  return arr;
};

export const isChatError = (obj: any): obj is BatchError => {
  return (
    obj &&
    typeof obj.message === "string" &&
    (typeof obj.prompt === "string" || obj.prompt === null) &&
    (typeof obj.model === "string" || obj.model === null) &&
    (typeof obj.response === "string" || obj.response === null)
  );
};
