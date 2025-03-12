module.exports = function (config) {
    config.set({
	// see https://github.com/karma-runner/karma-chrome-launcher/issues/158#issuecomment-339265457
        browsers: ['ChromeHeadlessNoSandbox'],
        customLaunchers: {
            ChromeHeadlessNoSandbox: {
                base: 'ChromeHeadless',
                flags: ['--no-sandbox']
            }
        },
        // The directory where the output file lives
        basePath: 'target/public/test/karma',
        // The file itself
        files: ['test.js'],
        frameworks: ['cljs-test'],
        plugins: ['karma-cljs-test', 'karma-chrome-launcher'],
        colors: true,
        logLevel: config.LOG_INFO,
        client: {
            args: ["shadow.test.karma.init"],
            // singleRun: true
        }
    });
};
// npm install karma-cljs-test --save-dev
