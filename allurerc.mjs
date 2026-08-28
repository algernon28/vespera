// Allure 3 report configuration, read by allure-maven through <configPath> in pom.xml.
// https://github.com/allure-framework/allure3
//
// allure-maven generates its own config and merges this file underneath it, overriding exactly
// three keys: the top-level name and output, and the Awesome plugin's singleFile. Those live in
// the pom; everything else belongs here.
export default {
  plugins: {
    awesome: {
      options: {
        // Group the tree by what a test claims, not by where its class happens to live.
        // Without this the tree folds on parentSuite/suite, which are the package and class
        // names -- io.algernon.vespera.corpus tells a reader nothing they wanted to know.
        // The labels come from @Epic/@Feature/@Story on the test classes and methods.
        groupBy: ["epic", "feature", "story"],
      },
    },
  },

  // Failure classification. Allure 3 supplies "Product errors" (failed) and "Test errors"
  // (broken) itself; these are the ones specific to this project, and they are matched on the
  // same @Feature labels the tree groups by, so a category names the capability that broke
  // rather than the exception that surfaced.
  categories: [
    {
      // AGENTS.md: a skipped test is not a passing test. Several cases abort by assumption when
      // the machine cannot create a symlink, an unpaired-surrogate filename, or is not Windows,
      // and a green run that quietly skipped them proves less than it appears to.
      name: "Environment could not host the case",
      matchers: { statuses: ["skipped"] },
      groupByMessage: true,
    },
    {
      name: "Docker was not available",
      matchers: { statuses: ["broken", "failed"], message: /Docker|Testcontainers/i },
    },
    {
      name: "File occurrence identity is broken",
      matchers: {
        statuses: ["failed", "broken"],
        labels: { feature: "File occurrence identity" },
      },
    },
    {
      name: "The walk is broken",
      matchers: { statuses: ["failed", "broken"], labels: { feature: "Walk" } },
    },
    {
      name: "A module boundary is broken",
      matchers: { statuses: ["failed", "broken"], labels: { feature: "Module boundaries" } },
    },
  ],
};
