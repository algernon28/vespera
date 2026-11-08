package io.algernon.vespera.ui;

/** The main class of {@link SleepingJar}: whatever its arguments, it sleeps until its process is killed. */
public final class ASleepingChildForTests {

    private ASleepingChildForTests() {
    }

    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(Long.MAX_VALUE);
    }
}
