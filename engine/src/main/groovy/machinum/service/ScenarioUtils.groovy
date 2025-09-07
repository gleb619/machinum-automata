package machinum.service

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.openqa.selenium.*
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait

import java.time.Duration
import java.util.NoSuchElementException

@Slf4j
@CompileStatic
@SuppressWarnings("unused")
class ScenarioUtils {

    private final Random random = new Random()
    private final LocalBrowserInstance browserInstance

    ScenarioUtils(LocalBrowserInstance browserInstance) {
        this.browserInstance = browserInstance
    }

    void open(String url) {
        driver.get(url)
    }

    void randomSleep(int minMs, int maxMs) {
        int sleepTime = random.nextInt(maxMs - minMs + 1) + minMs
        Thread.sleep(sleepTime)
    }

    boolean isElementPresent(String selector) {
        try {
            driver.findElement(By.cssSelector(selector))
            return true
        } catch (NoSuchElementException ignored) {
            return false
        }
    }

    void waitForElement(String selector, int timeoutSeconds = 10) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))
    }

    void waitForElementVisible(String selector, int timeoutSeconds = 10) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector)))
    }

    void waitForElementClickable(String selector, int timeoutSeconds = 10) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)))
    }

    void waitForElementText(String selector, String text, int timeoutSeconds = 10) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector(selector), text))
    }

    String takeScreenshot() {
        return browserInstance.takeScreenshot()
    }

    String getPageSource() {
        return browserInstance.getPageSource()
    }

    void scrollToElement(WebElement element) {
        executeJS("arguments[0].scrollIntoView(true);", element)
    }

    void scrollToTop() {
        executeJS("window.scrollTo(0, 0);")
    }

    void scrollToBottom() {
        executeJS("window.scrollTo(0, document.body.scrollHeight);")
    }

    Object executeJS(String script, Object... args) {
        if (driver instanceof JavascriptExecutor) {
            return ((JavascriptExecutor) driver).executeScript(script, args)
        }

        return null
    }

    void highlightElement(WebElement element) {
        executeJS("arguments[0].style.border='3px solid red';", element)
    }

    List<WebElement> findElements(String selector) {
        return driver.findElements(By.cssSelector(selector))
    }

    WebElement findElement(String selector) {
        return driver.findElement(By.cssSelector(selector))
    }

    void switchToFrame(String frameSelector) {
        WebElement frame = findElement(frameSelector)
        driver.switchTo().frame(frame)
    }

    void switchToDefaultContent() {
        driver.switchTo().defaultContent()
    }

    void switchToWindow(String windowHandle) {
        driver.switchTo().window(windowHandle)
    }

    Set<String> getWindowHandles() {
        return driver.getWindowHandles()
    }

    void acceptAlert() {
        driver.switchTo().alert().accept()
    }

    void dismissAlert() {
        driver.switchTo().alert().dismiss()
    }

    String getAlertText() {
        return driver.switchTo().alert().getText()
    }

    String capturePageHtml() {
        return browserInstance.capturePageHtml()
    }

    void changePageTitle(String newTitle) {
        executeJS("document.title = arguments[0];", newTitle)
    }

    void logDebug(String text, Object... args) {
        changePageTitle("${text.take(30)}...")
        log.debug(text, args)
    }

    void logDebug(String text) {
        logDebug(text, new Object[0])
    }

    void safeClick(WebElement element) {
        try {
            element.click()
        } catch (ElementClickInterceptedException ignore) {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE)

            Actions action = new Actions(driver)
            action.sendKeys(Keys.ESCAPE).build().perform()

            WebElement currentElement = driver.switchTo().activeElement()
            currentElement.sendKeys(Keys.ESCAPE)
        }
    }

    /* ========= */

    private WebDriver getDriver() {
        return browserInstance.getDriver()
    }

    //No operations class, for remote instance
    static class Noop {

    }

}
