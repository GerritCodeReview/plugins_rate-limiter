@PLUGIN@ list
=================

NAME
----
@PLUGIN@ list display rate limit statistics

SYNOPSIS
--------
>     ssh -p <port> <host> @PLUGIN@ list

DESCRIPTION
-----------
Displays rate limit statistics: account id (or IP if request is anonymous),
permits per hour, remaining permits and when they will be replenished.

The time before permits are replenished is represented using ISO-8601 seconds
based representation, such as PT59M30S (59 minutes and 30 seconds).

ACCESS
------
Gerrit Administrators only.

EXAMPLES
--------

>     $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ list
>     ------------------------------------------------------------------------------
>     * upload_pack_per_hour *
>     ------------------------------------------------------------------------------
>     Account Id (or IP)   Permits Per Hour  Available Permits   Replenish in
>     ------------------------------------------------------------------------------
>     1000001              unlimited         unlimited           PT0S
>     1000002              1000              999                 PT59M30S
>     127.0.0.1            1000              123                 PT10M26S
>     ------------------------------------------------------------------------------
