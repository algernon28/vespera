# Question

What specific wording should the ADR-040 amendment have regarding content-identity resolution moving from `ByteLevelReductionTasklet.java:219-279` to `corpus`?

## Notes

The plan quotes from `ByteLevelReductionTasklet.java:54-56`: "Lives here rather than in `corpus` because `corpus` does not know what a stage is."

The amendment needs to:
- Clarify why this change is valid despite that quote
- Explain that `corpus` now has knowledge of stages through its role in stage 1's run identity
- Specify that content-identity resolution is now a corpus concern
- Reference that this enables the re-minting behavior described in the wave

The content-identity resolution:
- Groups file occurrences by size
- Hashes within groups of two or more
- Resolves duplicates (ADR-057, ADR-067, ADR-069)
- Is now owned by `corpus`'s `ContentHash`, `DuplicateResolution` and `ContentIdentity`

## Blocking

Ticket 1: The callback shape depends on this change
Ticket 8: Test porting needs to know which classes move

## Part of

#339