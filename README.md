This is WordAnalysisTool targeting Android, Web, Desktop.

To build with WACS client configuration first run:

`./gradlew generateBuildConfig -PwacsClient="<CLIENT_ID>" -PwacsCallback="<REDIRECT_URI>" -PbaseApi="<BASE_API>"`

To build web client run:

`./gradlew buildWebDistribution`