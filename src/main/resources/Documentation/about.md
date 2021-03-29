This plugin allows to enforce rate limits in Gerrit.

The @PLUGIN@ plugin supports the following rate limits:

* `uploadpackperhour` requests per period which are executed when a client runs a fetch command.
* `timelapse` defines a period for the rate limiter.

Rate limits define the maximum request rate for users in a given group
for a given request type.
