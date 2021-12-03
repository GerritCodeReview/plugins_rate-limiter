based representation, such as PT59M30S (59 minutes and 30 seconds).
ACCESS
------
Gerrit Administrators only.
EXAMPLES
--------
>     $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ list
>     ---------------------------------------------------------------------------------------------
>     * upload_pack_per_hour *
>     ---------------------------------------------------------------------------------------------
>     Account Id/IP (username)   Permits Per Hour  Available Permits   Used Permits    Replenish in
>     ---------------------------------------------------------------------------------------------
>     1000000 (admin)            unlimited         unlimited           0               PT0S
>     1000001 (test_user)        1000              999                 1               PT59M30S
>     127.0.0.1                  1000              123                 877             PT10M26S
>     ---------------------------------------------------------------------------------------------
