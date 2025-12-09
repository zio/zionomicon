package zionomicon.exercises

package ConfiguringZIOApplications {

  /**
   *   1. Build a system that watches a configuration file for changes and
   *      automatically restarts an HTTP server with the updated settings. Your
   *      solution should gracefully stop the old server before starting a new
   *      one.
   */
  package ReloadableServiceImpl {}

  /**
   *   2. As your application becomes more complex, you may need to manage
   *      different configurations for different environments, with each in a
   *      separate file, such as `Development.conf`, `Testing.conf`, and
   *      `Production.conf`. Write a config provider that loads configurations
   *      based on the environment variable `APP_ENV`.
   */
  package EnvironmentBasedConfigImpl {}

  /**
   *   3. Write a configuration descriptor for the `DatabaseConfig` which only
   *      accepts either `MysqlConfig` or `SqliteConfig` configurations.
   */
  package DatabaseConfigImpl {}

}
