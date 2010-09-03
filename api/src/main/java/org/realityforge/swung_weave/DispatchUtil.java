package org.realityforge.swung_weave;

import java.awt.EventQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class DispatchUtil
{
  public static <V> V invokeAndWait( final Callable<V> function )
    throws Exception
  {
    if( EventQueue.isDispatchThread() )
    {
      throw new IllegalStateException( "Should not be calling invokeAndWait from EDT" );
    }
    try
    {
      final FutureTask<V> task = new FutureTask<V>( function );
      EventQueue.invokeLater( task );
      return task.get();
    }
    catch( final ExecutionException e )
    {
      final Throwable cause = e.getCause();
      if( cause instanceof RuntimeException )
      {
        throw (RuntimeException)cause;
      }
      else if( cause instanceof Error )
      {
        throw (Error)cause;
      }      
      else //if( cause instanceof Exception )
      {
        throw (Exception)cause;
      }
    }
    catch( final InterruptedException ie )
    {
      throw new RuntimeException( ie );
    }
  }
}
