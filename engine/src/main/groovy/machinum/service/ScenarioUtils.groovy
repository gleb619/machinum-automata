package machinum.service

import groovy.util.logging.Slf4j
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait

import java.time.Duration

@Slf4j
class ScenarioUtils {

    private final BrowserInstance browserInstance
    private final Random random = new Random()

    ScenarioUtils(BrowserInstance browserInstance) {
        this.browserInstance = browserInstance
    }

    void randomSleep(int minMs, int maxMs) {
        int sleepTime = random.nextInt(maxMs - minMs + 1) + minMs
        Thread.sleep(sleepTime)
    }

    boolean isElementPresent(String selector) {
        try {
            browserInstance.getDriver().findElement(By.cssSelector(selector))
            return true
        } catch (NoSuchElementException e) {
            return false
        }
    }

    void waitForElement(String selector, int timeoutSeconds = 10) {
        WebDriverWait wait = new WebDriverWait(browserInstance.getDriver(), Duration.ofSeconds(timeoutSeconds))
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))
    }

    void waitForElementVisible(String selector, int timeoutSeconds = 10) {
        WebDriverWait wait = new WebDriverWait(browserInstance.getDriver(), Duration.ofSeconds(timeoutSeconds))
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector)))
    }

    void waitForElementClickable(String selector, int timeoutSeconds = 10) {
        WebDriverWait wait = new WebDriverWait(browserInstance.getDriver(), Duration.ofSeconds(timeoutSeconds))
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)))
    }

    String takeScreenshot() {
        return browserInstance.takeScreenshot()
    }

    String getPageSource() {
        return browserInstance.getPageSource()
    }

    void scrollToElement(WebElement element) {
        ((JavascriptExecutor) browserInstance.getDriver())
                .executeScript("arguments[0].scrollIntoView(true);", element)
    }

    void scrollToTop() {
        executeJS("window.scrollTo(0, 0);")
    }

    void scrollToBottom() {
        executeJS("window.scrollTo(0, document.body.scrollHeight);")
    }

    Object executeJS(String script, Object... args) {
        return ((JavascriptExecutor) browserInstance.getDriver()).executeScript(script, args)
    }

    void highlightElement(WebElement element) {
        executeJS("arguments[0].style.border='3px solid red';", element)
    }

    List<WebElement> findElements(String selector) {
        return browserInstance.getDriver().findElements(By.cssSelector(selector))
    }

    WebElement findElement(String selector) {
        return browserInstance.getDriver().findElement(By.cssSelector(selector))
    }

    void switchToFrame(String frameSelector) {
        WebElement frame = findElement(frameSelector)
        browserInstance.getDriver().switchTo().frame(frame)
    }

    void switchToDefaultContent() {
        browserInstance.getDriver().switchTo().defaultContent()
    }

    void switchToWindow(String windowHandle) {
        browserInstance.getDriver().switchTo().window(windowHandle)
    }

    Set<String> getWindowHandles() {
        return browserInstance.getDriver().getWindowHandles()
    }

    void acceptAlert() {
        browserInstance.getDriver().switchTo().alert().accept()
    }

    void dismissAlert() {
        browserInstance.getDriver().switchTo().alert().dismiss()
    }

    String getAlertText() {
        return browserInstance.getDriver().switchTo().alert().getText()
    }

}
