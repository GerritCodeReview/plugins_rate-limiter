@PLUGIN@ - /replication/ REST API
===================================
This page describes the REST endpoint that is added by the @PLUGIN@
plugin.
Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).
This API implements a REST equivalent of the Ssh rename-project command.
For more information, refer to:
* [Ssh list command](cmd-list.md)
* [Ssh replenish command](cmd-replenish.md)
------------------------------------------
REQUEST
-------
```
GET /plugins/rate-limiter/list HTTP/1.0
```
To get list of rate limit statistics.

RESPONSE
--------
```
[
  {
    "AccountId": "1000000 (admin)",
    "permits_per_hour": "unlimited",
    "available_permits": "unlimited",
    "used_permit": "0",
    "replenish_in": "PT0S"
  },
  {
    "AccountId": "1000001 (testUser)",
    "permits_per_hour": "unlimited",
    "available_permits": "unlimited",
    "used_permit": "0",
    "replenish_in": "PT0S"
  }
]
```

REQUEST
-------
```
POST /plugins/rate-limiter/repenish HTTP/1.0
```

Replenish permits for a given remotehost/user, or all. Uses parameters to specify
which permit to replenish.

To replenish all permits for user ```admin``` use:

```
POST /plugins/rate-limiter/replenish?user=ttyt&user=admin HTTP/1.0
```

To replenish all permits for remotehost ```127.0.0.0``` use:

```
POST /plugins/rate-limiter/replenish?user=ttyt&host=127.0.0 HTTP/1.0
```

To replenish all permits use:
```
POST /plugins/rate-limiter/replenish?user=ttyt&all=true HTTP/1.0
```

RESPONSE
--------
```
HTTP/1.1 204 NO_CONTENT
```

ACCESS
------
Same as ssh version of the command caller must be a member of a group that is granted
the 'Administrate Server' capability.

