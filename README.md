This is WordAnalysisTool targeting Android, Web, Desktop.

To build with Api and WACS client configuration first run:

`./gradlew generateBuildConfig -PwacsClientId="<WACS_CLIENT_ID>" -PwatBaseUrl="<WAT_BASE_URL>"`

To build web client:

1. Run `./gradlew buildWebDistribution`
2. Create database tables `npx wrangler d1 execute batches --remote --file=./schema.sql`
3. Deploy api with client `cd api && npm run deploy`