This is WordAnalysisTool targeting Android, Web, Desktop.

# Secrets

The app uses cloudflare as an API. Here are the secrets that should be created:

`JWT_SECRET_KEY` - Secret key to sign JWT access tokens  

`OPENAI_API_KEY` - [OpenAI API key](https://platform.openai.com)  
`QWEN_API_KEY` - [Alibaba API key](https://home.console.alibabacloud.com)  
`CLAUDEAI_API_KEY` - [Anthropic API key](https://console.anthropic.com)  
`MISTRAL_API_KEY` - [Mistral AI API key](https://console.mistral.ai)  

WACS client configuration [obtain here](https://content.bibletranslationtools.org/user/settings/applications):

`WACS_CLIENT` - WACS Client ID  
`WACS_SECRET` - WACS Client Secret  
`WACS_CALLBACK` - WACS Redirect Uri  

# Configuration

To build with Api and WACS client configuration, first run:

`./gradlew generateBuildConfig -PwacsClientId="<WACS_CLIENT_ID>" -PwatBaseUrl="<WAT_BASE_URL>"`

# Database

In `api/wrangler.jsonc` file change `database_id` to your database id.

Create database tables in cloudflare by running:

`npx wrangler d1 execute batches --remote --file=./schema.sql`

# Build

To build web client:

1. Run `./gradlew buildWebDistribution`
2. Deploy api with client `cd api && npm run deploy`

To build desktop clients (on a corresponding OS only) run:

`./gradlew packageDmg` - for Mac  
`./gradlew packageMsi` - for Windows  
`./gradlew packageDeb` - for Linux  

To build android client run:

`./gradlew assembleRelease` or use Android Studio tools
