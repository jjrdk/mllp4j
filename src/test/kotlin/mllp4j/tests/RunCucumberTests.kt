package mllp4j.tests


import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(features = ["src/test/resources/features"], stepNotifications = true, tags = "not @ignore")
class RunCucumberTest