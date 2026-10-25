# Anonmous access

## Baseline severity

Low

## Authentication mode

Anonymous

## OOB required

False

## OWASP reference

None

## What to do

Send a the baseline request wittout any authentication headers.

## What to look for

If the request is accepted then it is a finding. An accepted request return a HTTP response status code from 200 to 299 or from 500 to 599.
