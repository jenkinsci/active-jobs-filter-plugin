# active-jobs-filter-plugin

## Introduction

Active Jobs Filter Plugin adds a job filter for standard Jenkins views that shows only items which
have started a build within a configurable number of days. It can filter by job type and supports
allow and deny patterns (as regular expressions) for job full names.

- Keep list views focused on jobs that are still being used.
- Hide stale jobs without deleting them or changing their configuration.
- Keep multibranch projects visible when one of their included branches has recent activity.

Key behaviors:

- Activity is based on the timestamp of the most recent build start time.
- Deny pattern takes precedence over allow pattern.
- `0` active days includes any job that has started at least one build.
- Multibranch change-request jobs (such as PR builds) are excluded by default.
- Branch jobs inside multibranch projects are shown only when the list view has recursion enabled.
- Regular expressions use Java `matches()` semantics, so they must match the whole job full name.

## Getting started

1. Install the plugin and restart Jenkins.
2. Create or configure a standard list view:
   - View -> Configure -> Job Filters
   - Add **Active jobs filter**
3. Configure the fields:
   - **Active in last (days)**: number of days since the last build start time before a job is
     excluded. Set to `0` to include any job that has run at least once.
   - **Job type**: all, pipeline, multibranch pipeline, or freestyle.
   - **Include PRs for multibranch jobs**: include multibranch change-request jobs when evaluating
     branch jobs or multibranch project activity.
   - **Regexp allow**: Java regular expression to include matching job full names. Empty means no
     allow filtering.
   - **Regexp deny**: Java regular expression to exclude matching job full names. Deny wins over
     allow.

Job DSL example:

```
listView('Active jobs') {
  recurse(true)
  jobs {
    regex('.*')
  }
  jobFilters {
    activeJobsFilter {
      activeDays(14)
      jobType('ALL')
      includeMultibranchPrs(false)
      allowRegex('.*')
      denyRegex('.*experimental.*')
    }
  }
}
```

Build from source:

```
mvn package
```

The built plugin is written to `target/active-jobs-filter.hpi`.

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
