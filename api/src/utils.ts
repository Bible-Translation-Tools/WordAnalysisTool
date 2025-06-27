import { SplitBatchJson } from "./types";

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

export const splitBatchJson = (json: string) => {
  const searchString = '"output":[]';
  const outputStart = json.indexOf(searchString);
  const outputEnd = outputStart + searchString.length;

  const output = json.substring(outputStart, outputEnd);

  const bracketStart = output.indexOf("[");
  const bracketEnd = output.indexOf("]");
  const bracketSize = bracketEnd - bracketStart;

  const finalStart = outputStart + bracketStart + 1;
  const finalEnd = outputEnd - bracketSize;

  const leftPart = json.substring(0, finalStart);
  const rightPart = json.substring(finalEnd, json.length);

  const splitJson: SplitBatchJson = {
    left: leftPart,
    right: rightPart,
  };

  return splitJson;
};
