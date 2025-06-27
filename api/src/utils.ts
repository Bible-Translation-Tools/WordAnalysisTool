export const isAdmin = (username: string, env: CloudflareBindings) => {
  const admins = env.WAT_ADMINS.split(",");
  return admins.includes(username);
};
