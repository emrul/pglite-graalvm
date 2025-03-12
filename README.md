
# Attempt to run Postgres WASI module under GraalWasm in Single-user mode

Doesn't work currently but maybe we'll get it working.

- [X] Get module loading
- [ ] Run basic query (`SELECT now()`) and print output
- [ ] Make sure persistence is working
- [ ] Implement wire protocol
- [ ] Refactor example into a usable library

Current state: Doesn't get past `pg_initdb` :-(

Current output is:

```
- *** Executing '_start' *** -
=setup=
 >>>>>>>>>>>>> FORCING EMBED MODE <<<<<<<<<<<<<<<<
# 1095: argv0 (--single) PGUSER=postgres PGDATA=/tmp/pglite/base PGDATABASE=postgres PGEMBED=(null) REPL=Y
# ============= argv dump ==================
--single postgres 
# ============= arg->env dump ==================

# =========================================
# ============= env dump ==================
# ENVIRONMENT=wasi-embed
# REPL=Y
# PGUSER=postgres
# PGDATABASE=postgres
# PGDATA=/tmp/pglite/base
# PGSYSCONFDIR=/tmp/pglite
# PGCLIENTENCODING=UTF8
# LC_CTYPE=C
# TZ=UTC
# PGTZ=UTC
# PG_COLOR=always
# =========================================
# no '/tmp' directory, creating one ...
# no '/tmp/pglite' directory, creating one ...



   @@@@@@@@@@@@@@@@@@@@@@@@@ EXITING with live runtime port 1 @@@@@@@@@@@@@@@@


# 1267: argv0 (/tmp/pglite/bin/postgres) PGUSER=postgres PGDATA=/tmp/pglite/base PGDATABASE=postgres PGEMBED=(null) REPL=Y


    setup: is_embed : not running initdb


- *** Executing 'pg_initdb' *** -
# 1066: pg_initdb()
# 1080: pg_initdb: db exists at : /tmp/pglite/base TODO: test for db name : postgres 
00000000000000000000000000001101
LOG:  invalid binary "/tmp/pglite/bin/postgres": No such file or directory
LOG:  invalid binary "/tmp/pglite/bin/postgres": No such file or directory
# 47
WARNING:  /tmp/pglite/bin/postgres:212: could not locate my own executable path
# 53
# 56
# 1228: CreateLockFile(postmaster.pid) w+ (forced)
# 91
2025-03-12 12:25:28.773 GMT [66600] DEBUG:  mmap(144703488) with MAP_HUGETLB failed, huge pages disabled: Invalid argument
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# FIXING: int shmget (key_t __key=87391111, size_t __size=40, int __shmflg=1920) pagesize default=65536
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# FIXING: void *shmat (int __shmid=666, const void *__shmaddr=0, int __shmflg=0)
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 287: shm_open(/tmp/PostgreSQL.1515174860) => 5
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 130
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 415:/data/git/pglite/postgresql-REL_16_STABLE/src/backend/storage/ipc/ipc.c on_shmem_exit(pg_on_exit_callback function, Datum arg) STUB
# 224: schedule_alarm(TimestampTz now)
2025-03-12 12:25:29.106 GMT [66600] LOG:  database system was interrupted; last known up at 2025-01-16 10:50:39 GMT
# 117(FATAL): insert_timeout(TimeoutId id=11, int index=0): /data/git/pglite/postgresql-REL_16_STABLE/src/backend/utils/misc/timeout.c
# 224: schedule_alarm(TimestampTz now)
2025-03-12 12:25:29.435 GMT [66600] DEBUG:  checkpoint record is at 0/A89408
2025-03-12 12:25:29.437 GMT [66600] DEBUG:  redo record is at 0/A89408; shutdown true
2025-03-12 12:25:29.439 GMT [66600] DEBUG:  next transaction ID: 1039; next OID: 32768
2025-03-12 12:25:29.441 GMT [66600] DEBUG:  next MultiXactId: 1; next MultiXactOffset: 0
2025-03-12 12:25:29.443 GMT [66600] DEBUG:  oldest unfrozen transaction ID: 723, in database 1
2025-03-12 12:25:29.447 GMT [66600] DEBUG:  oldest MultiXactId: 1, in database 1
2025-03-12 12:25:29.450 GMT [66600] DEBUG:  commit timestamp Xid oldest/newest: 0/0
2025-03-12 12:25:29.452 GMT [66600] LOG:  database system was not properly shut down; automatic recovery in progress
2025-03-12 12:25:29.455 GMT [66600] DEBUG:  transaction ID wrap limit is 2147484370, limited by database with OID 1
2025-03-12 12:25:29.458 GMT [66600] DEBUG:  MultiXactId wrap limit is 2147483648, limited by database with OID 1
2025-03-12 12:25:29.462 GMT [66600] DEBUG:  starting up replication slots
2025-03-12 12:25:29.466 GMT [66600] DEBUG:  xmin required by slots: data 0, catalog 0
2025-03-12 12:25:29.469 GMT [66600] PANIC:  could not read file "pg_logical/replorigin_checkpoint": read 0 of 16
```