Notification emails
=============

When user reaches "soft" of "hard" limit of requests per period, plugin checks, if user is a member
of one of the groups, defined in configuration in 'softlimit' or 'hardlimit' setting
correspondingly. If yes, plugin sends notification email to user email address.

Email example, when user reaches "soft" limit:

```
  [Gerrit Code Review] User "username" reached the warning limit of "uploadpackperhourwarn"
  upload pack per "timelapseinminutes" minutes.
  This email is to inform that a user with "email@address" has exceeded a rate limit.

  Log: User "username" reached the warning limit of "uploadpackperhourwarn" upload pack per
  "timelapseinminutes" minutes.

  This is a send-only email address. Replies to this message will not be read or answered.
```

Email example, when user reaches "soft" limit:

```
  [Gerrit Code Review] User "username" was blocked due to exceeding the limit of "uploadpackperhour"
  upload pack per "timelapseinminutes" minutes. XX min YY sec remaining to permits replenishing.
  This email is to inform that a user with "email@address" has exceeded a rate limit.

  Log: User "username" was blocked due to exceeding the limit of "uploadpackperhour"
  upload pack per "timelapseinminutes" minutes. XX min YY sec remaining to permits replenishing.

  This is a send-only email address. Replies to this message will not be read or answered.
```

Notification email module is encapsulated in
com.googlesource.gerrit.plugins.ratelimiter.RateLimitReachedSender class. It uses
RateLimiterEmailFormat.soy and RateLimiterEmailFormatHTML.soy templates. Templates are located in
resources. It's designed to minimize user impact to email templates.
