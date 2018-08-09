@PLUGIN@ replenish
======================

NAME
----
@PLUGIN@ replenish

SYNOPSIS
--------
>     ssh -p <port> <host> @PLUGIN@ replenish
>      [--all]
>      [--user] <USER>
>      [--remotehost] <REMOTEHOST>

DESCRIPTION
-----------
Replenishes all uploadpackperhour permits for a given remotehost/user, or all.

PARAMETERS
----------

`remotehost`
> Remote host to replenish permits for.

`user`
> User to replenish permits for.

ACCESS
------
Gerrit Administrators only.

OPTIONS
-------
`--remotehost`
> Replenish permits for a given remote host.

`--user`
> Replenish permits for a given user.

EXAMPLES
--------
Replenish all permits for a remotehost 127.0.0.1
>     $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ replenish --remotehost 127.0.0.1

Replenish all permits for a user 'admin'
>     $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ replenish --user admin

Replenish all permits
>     $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ replenish --all
