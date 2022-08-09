This plugin allows to enforce rate limits in Gerrit.

The @PLUGIN@ plugin supports the following rate limits:

* `uploadpackperhour` requests per period which are executed when a client runs a fetch command.
* `uploadpackperhourwarn` soft limit of requests per period when a client runs a fetch command.
* `timelapseinminutes` defines a period in minutes for the rate limiter. This value supports a
  limit of 60.

Plugin has a functionality to send notification by email when warn or hard limit
is reached.

Rate limits define the maximum request rate for users in a given group
for a given request type.
