package org.threadly.concurrent.processing;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.ThreadlyTester;
import org.threadly.concurrent.ConfigurableThreadFactory;
import org.threadly.concurrent.StartingThreadFactory;
import org.threadly.test.concurrent.TestCondition;
import org.threadly.util.TestExceptionHandler;

@SuppressWarnings({"javadoc", "deprecation"})
public class BlockingQueueConsumerTest extends ThreadlyTester {
   private SynchronousQueue<Object> queue;
   private TestAcceptor acceptor;
   private BlockingQueueConsumer<Object> queueConsumer;
   
  @Before
  public void setup() {
    queue = new SynchronousQueue<>();
    acceptor = new TestAcceptor();
    queueConsumer = BlockingQueueConsumer.makeForHandlers(new ConfigurableThreadFactory(), queue, 
                                                          acceptor, null);
  }
  
  @After
  public void cleanup() {
    queueConsumer.stopIfRunning();
    queue = null;
    acceptor = null;
    queueConsumer = null;
  }
  
  @SuppressWarnings("unused")
  @Test
  public void constructorFail() {
    try {
      BlockingQueueConsumer.makeForHandlers(null, new SynchronousQueue<>(), 
                                            new TestAcceptor(), null);
      fail("Exception should have been thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      BlockingQueueConsumer.makeForHandlers(new ConfigurableThreadFactory(), new SynchronousQueue<>(), 
                                            null, null);
      fail("Exception should have been thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      BlockingQueueConsumer.makeForHandlers(new ConfigurableThreadFactory(), null, 
                                            new TestAcceptor(), null);
      fail("Exception should have been thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
  
  @Test
  public void doubleStartTest() {
    // start queue
    queueConsumer.startIfNotStarted();
    
    assertTrue(queueConsumer.isRunning());
    
    // attempt to start again
    queueConsumer.startIfNotStarted();
    // should still be running without exception
    assertTrue(queueConsumer.isRunning());
  }
  
  @Test (expected = IllegalThreadStateException.class)
  public void startFail() {
    StartingThreadFactory threadFactory = new StartingThreadFactory();
    try {
      queueConsumer = BlockingQueueConsumer.makeForHandlers(threadFactory, queue, acceptor, null);
      queueConsumer.start();
    } finally {
      threadFactory.killThreads();
    }
  }
  
  @Test
  public void doubleStopTest() {
    queueConsumer.start();
    assertTrue(queueConsumer.isRunning());
    
    queueConsumer.stopIfRunning();
    assertFalse(queueConsumer.isRunning());
    
    queueConsumer.stopIfRunning();
    assertFalse(queueConsumer.isRunning());
  }
  
  @Test
  public void consumeTest() throws InterruptedException {
    assertFalse(queueConsumer.isRunning());
    
    // start queue
    queueConsumer.start();
    
    assertTrue(queueConsumer.isRunning());
    
    Object item = new Object();
    queue.put(item);
    
    acceptor.blockTillTrue(); // will throw exception if never got item
    
    assertTrue(acceptor.acceptedItems.get(0) == item);
  }
  
  @Test
  public void consumeExceptionTest() throws InterruptedException {
    final TestExceptionHandler teh = new TestExceptionHandler();
    final RuntimeException e = new RuntimeException();
    BlockingQueueConsumer<Object> queueConsumer = 
        BlockingQueueConsumer.makeForHandlers(new ConfigurableThreadFactory(), 
                                              queue, (item) -> { throw e; }, teh);
    try {
      queueConsumer.start();
      
      Object item = new Object();
      queue.put(item);
      
      // will throw exception if test fails
      new TestCondition(teh::getLastThrowable, (t) -> t == e).blockTillTrue();
      
      // verify thread did not die
      assertTrue(queueConsumer.runningThread.isAlive());
    } finally {
      queueConsumer.stop();
    }
  }
  
  private static class TestAcceptor extends TestCondition implements Consumer<Object> {
    private final List<Object> acceptedItems = new LinkedList<>();
    
    @Override
    public void accept(Object item) {
      synchronized (this) {
        acceptedItems.add(item);
      }
    }

    @Override
    public boolean get() {
      synchronized (this) {
        return ! acceptedItems.isEmpty();
      }
    }
  }
}
