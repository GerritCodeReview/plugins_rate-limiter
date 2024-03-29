Configuration
=============

Rate Limits
-----------

In case gerrit is not replica, the defined rate limits are stored in a
`rate-limiter.config` file in the `{review_site}/etc` directory or in a
`refs/meta/config` of `All-Projects`. In case `rate-limiter.config` is present
in both `{review_site}/etc` and `refs/meta/config` of `All-Projects`, the
configuration will be applied from `refs/meta/config` of `All-Projects`. If
`rate-limiter.config` is invalid in `All-Projects` then `rate-limiter.config`
will be applied from `{review_site}/etc`. Rate limits are defined per user group
and rate limit type.
If `rate-limiter.config` is missing from  or corrupted in both
`{review_site}/etc` and `refs/meta/config` then all users will have unlimited
permits.

In case gerrit is replica, the configuration will only load from be applied from
`{review_site}/etc`.

Notification user groups are defined by setting 'recipients' in 'sendemail'
section. The 'recipients' property specifies user groups, which members will
receive notification emails.

Example:

```
  [group "buildserver"]
    uploadpackperhour = 10
    uploadpackperhourwarn = 8
    timelapseinminutes = 10

  [group "Registered Users"]
    uploadpackperhour = 1

  [group "Anonymous Users"]
    uploadpackperhour = 6

  [group "gerrit-user"]
    uploadpackperhourwarn = 10

  [sendemail]
    recipients = Access_Group_1, Access_Group_2, Access_Group_3
```

In this example, the plugin will apply the uploadpackperhour and
uploadpackperhourwarn properties every 10 minutes for the members of
the group buildserver and every 1 hour for the other groups. Therefore,
the members of the group buildserver will have a limit of 10 uploaded
packs every 10 minutes.

For logged-in users, rate limits are associated to their accountId. For
anonymous users, rate limits are associated to their remote host address.
If multiple anonymous users are accessing Gerrit via the same host (e.g.,
a proxy), then they share a common rate limit.

If a user is a member of multiple groups mentioned in `rate-limiter.config`,
the limit that applies is defined first in the `rate-limiter.config` file.
This resolves ambiguity in case the user is a member of multiple groups
used in the configuration.

Use group "Anonymous Users" to define the rate limit for anonymous users.
Use group "Registered Users" to define the default rate limit for all logged-in
users.

The recipients property of sendemail section define that emails about reaching
soft and hard limits will be sent to members of `Access_Group_1, Access_Group_2,
Access_Group_3` access groups.

A second, "soft" limit can be defined for every rate limit, to warn
administrators about the users that can be affected by an upcoming lower limit.
In this case, the "soft" limit has the same name as the original limit, but
ends with the "warn" suffix. For example, for the `uploadpackperhour` limit,
its "soft" counterpart will be called `uploadpackperhourwarn`:

```
  [group "Registered Users"]
    uploadpackperhour = 100
    uploadpackperhourwarn = 50
```

When a registered user reaches the "soft" limit (50 uploads for the example),
a warn message is logged in the `RateLimiterStatsLog`, located in the
`<gerrit_site>/logs` folder:

```
  [2018-06-04 05:40:36,006] user reached the limit of 50
```

The upload limitation will be enforced, i.e., the operation will be blocked,
only when the user reaches 100 uploads.

When "soft" or "hard" limit is reached, user gets a notification email, if user
is a member of one of the groups, defined in 'rate-limiter.config' (unless it's
not an Anonymous user).

NOTE: If limit is reached for Anonymous user or user who does not have an email
(e.g. functional user), the email won't be sent as user will have link to
anonymous query only. There will be also log message generated in rate-limiter
log file while sending email:

```
  Error with exception while sending email:
  com.google.gerrit.exceptions.NoSuchAccountException: Not Found: User not found
```

If the warn limit is present in the configuration but no hard limit, then no
limit will be enforced, but a log entry will be written when the user reaches
the warning limit.

Format of the rate limit entries in `rate-limiter.config`:

```
  [group "<groupName>"]
    <rateLimitType> = <rateLimit>
```

<a id="rateLimitType>">
`group.<groupName>.rateLimitType`
: identifies which request type is limited by this configuration.
The following rate limit types are supported:
* `uploadpackperhour`: rate limit for uploadpack (fetch) requests.

The group can be defined by its name or UUID.

<a id="uploadpackperhour">
`group.<groupName>.uploadpackperhour`
: configures the rate limit of fetch requests for the given group.

If a rate limit configuration value is invalid, a default rate limit of
1000 requests per hour is assumed.

Example:

Configures a rate limit of maximum 30 fetch requests per hour for the
group of registered users.

```
  [group "Registered Users"]
    uploadpackperhour = 30
```

The rate limit exceeded message can be configured by setting the
`configuration.uploadpackLimitExceededMsg` parameter in the
`rate-limiter.config` file. The `${rateLimit}` token is supported in the
message and will be replaced by the effective rate limit per hour.

Defaults to `Exceeded rate limit of ${rateLimit} fetch requests/hour`.

`timelapseinminutes` defines a period of time in which the limit of
uploadpack takes place. If it is not configured, a default value of 1 hour
is established.

If `rate-limiter.config` is changed in `All-Projects` then rate limit will reset for users.
