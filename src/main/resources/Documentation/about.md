This plugin allows to enforce rate limits in Gerrit.

The @PLUGIN@ plugin supports the following rate limits:

* `uploadpackperhour` requests per period which are executed when a client runs a fetch command.
* `timelapseinminutes` defines a period in minutes for the rate limiter. This value supports a
  limit of 60.

Rate limits define the maximum request rate for users in a given group
for a given request type.
