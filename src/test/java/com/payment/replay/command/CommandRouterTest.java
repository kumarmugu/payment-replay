package com.payment.replay.command;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandRouterTest {

    private CommandRouter router;
    private TestCommand testCommand;

    @Before
    public void setUp() {
        router = new CommandRouter();
        testCommand = new TestCommand();
        router.register(testCommand);
    }

    @Test
    public void shouldRouteToRegisteredCommand() {
        int result = router.route(new String[]{"test-cmd", "arg1"});

        assertThat(result).isEqualTo(0);
        assertThat(testCommand.wasExecuted).isTrue();
        assertThat(testCommand.receivedArgs).containsExactly("arg1");
    }

    @Test
    public void shouldReturnErrorForUnknownCommand() {
        int result = router.route(new String[]{"unknown"});

        assertThat(result).isEqualTo(1);
    }

    @Test
    public void shouldReturnErrorForEmptyArgs() {
        int result = router.route(new String[]{});

        assertThat(result).isEqualTo(1);
    }

    @Test
    public void shouldReturnErrorForNullArgs() {
        int result = router.route(null);

        assertThat(result).isEqualTo(1);
    }

    @Test
    public void shouldHandleHelpFlag() {
        int result = router.route(new String[]{"--help"});

        assertThat(result).isEqualTo(0);
    }

    @Test
    public void shouldBeCaseInsensitive() {
        int result = router.route(new String[]{"TEST-CMD"});

        assertThat(result).isEqualTo(0);
        assertThat(testCommand.wasExecuted).isTrue();
    }

    /**
     * Simple test command for verifying routing behavior.
     */
    private static class TestCommand implements Command {
        boolean wasExecuted = false;
        String[] receivedArgs;

        @Override
        public int execute(String[] args) {
            wasExecuted = true;
            receivedArgs = args;
            return 0;
        }

        @Override
        public String getName() {
            return "test-cmd";
        }

        @Override
        public String getDescription() {
            return "Test command";
        }

        @Override
        public String getUsage() {
            return "test-cmd [args]";
        }
    }
}
