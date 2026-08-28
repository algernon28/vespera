// Allure 3 report configuration, read by allure-maven through <configPath> in pom.xml.
// https://github.com/allure-framework/allure3
//
// allure-maven generates its own config and merges this file underneath it, overriding exactly
// three keys: the top-level name and output, and the Awesome plugin's singleFile. Those live in
// the pom; everything else belongs here.
// noinspection JSUnusedGlobalSymbols

export default {
    categories: [
        {
            // AGENTS.md: a skipped test is not a passing test. Several cases abort by assumption when
            // the machine cannot create a symlink, an unpaired-surrogate filename, or is not Windows,
            // and a green run that quietly skipped them proves less than it appears to.
            name: "Environment could not host the case",
            matchers: {statuses: ["skipped"]},
            groupByMessage: true,
        },
        {
            name: "Docker was not available",
            matchers: {statuses: ["broken", "failed"], message: /Docker|Testcontainers/i},
        },
        {
            name: "File occurrence identity is broken",
            matchers: {
                statuses: ["failed", "broken"],
                labels: {feature: "File occurrence identity"},
            },
        },
        {
            name: "The walk is broken",
            matchers: {statuses: ["failed", "broken"], labels: {feature: "Walk"}},
        },
        {
            name: "A module boundary is broken",
            matchers: {statuses: ["failed", "broken"], labels: {feature: "Module boundaries"}},
        },
    ],
    plugins: {
        awesome: {
            options: {
                singleFile: true,
                groupBy: ["epic", "feature", "story"],
            },
        },
    },
};


