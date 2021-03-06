package targetWithSeparateHooks;

import android.support.test.runner.AndroidJUnit4;
import java.lang.Throwable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import steps.JustHooks;
import steps.OtherSteps;
import steps.Steps;

@RunWith(AndroidJUnit4.class)
public class AFeatureWithBackgroundTest {

    private final Steps steps_Steps = new Steps();
    private final OtherSteps steps_OtherSteps = new OtherSteps();
    private final JustHooks steps_JustHooks = new JustHooks();

    @Before
    public void setUp() throws Throwable {
        steps_JustHooks.beforeHook();
    }

    @After
    public void tearDown() throws Throwable {
        steps_JustHooks.afterHook();
    }

    @Test
    public void scenarioWithOneStepAndBackground() throws Throwable {
        steps_Steps.aStepWithoutParameters();
        steps_OtherSteps.aStepFromAnotherDefinitionFile();
    }
}
