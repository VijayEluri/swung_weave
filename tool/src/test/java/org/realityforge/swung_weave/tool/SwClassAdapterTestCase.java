package org.realityforge.swung_weave.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.realityforge.swung_weave.DisallowsEDT;
import org.realityforge.swung_weave.RequiresEDT;
import org.realityforge.swung_weave.RunInEDT;
import org.realityforge.swung_weave.RunOutsideEDT;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SwClassAdapterTestCase
{
  private static Class c_clazz;

  @BeforeClass
  public static void adaptClass()
    throws Exception
  {
    final String classname = "org.realityforge.swung_weave.tool.ClassToWeave";
    final ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_MAXS );
    final ClassReader cr = new ClassReader( classname );
    final SwClassAdapter adapter = new SwClassAdapter( cw );
    cr.accept( adapter, 0 );

    final HashMap<String, byte[]> classData = new HashMap<String, byte[]>();
    classData.putAll( adapter.getClassData() );
    classData.put( classname, cw.toByteArray() );

    if( false )
    {
      for( final Map.Entry<String, byte[]> entry : classData.entrySet() )
      {
        final String baseDir = "target/sw/";
        final File file = new File( baseDir + entry.getKey().replace( '.', '/' ) + ".class" );
        file.getParentFile().mkdirs();

        final FileOutputStream fos = new FileOutputStream( file );
        fos.write( entry.getValue() );
        fos.close();
      }
    }

    c_clazz = new ClassLoader()
    {
      public Class loadClass( final String name )
        throws ClassNotFoundException
      {
        final byte[] data = classData.get( name );
        if( null != data )
        {
          return defineClass( name, data, 0, data.length );
        }
        else if( TestInvocation.class.getName().equals( name ) )
        {
          return TestInvocation.class;
        }
        else
        {
          return super.loadClass( name );
        }
      }
    }.loadClass( classname );
  }

  @Test( dataProvider = "scenarios" )
  public void verifySwClassAdapterBehaviour( final TestInvocation invocation )
    throws Throwable
  {
    System.out.println();
    System.out.println();
    System.out.println();
    System.out.println( "=====================================" );
    System.out.println( invocation );
    System.out.println( "=====================================" );
    if( invocation.inEDT )
    {
      try
      {
        SwingUtilities.invokeAndWait( new Runnable()
        {
          @Override
          public void run()
          {
            invokeMethod( invocation );
          }
        } );
      }
      catch( final InvocationTargetException ite )
      {
        throw ite.getCause();
      }
    }
    else
    {
      invokeMethod( invocation );
    }
  }

  private void invokeMethod( final TestInvocation invocation )
  {
    TestInvocation.setCurrent( invocation );
    final String methodName = invocation.getMethodName();
    boolean completed = false;
    try
    {
      final Method method = c_clazz.getDeclaredMethod( methodName, new Class[0] );
      method.setAccessible( true );
      final Object instance;
      if( invocation.methodType == TestInvocation.INSTANCE )
      {
        instance = c_clazz.newInstance();
      }
      else
      {
        instance = null;
      }
      method.invoke( instance );
      completed = true;
    }
    catch( final InvocationTargetException e )
    {
      invocation.assertMatchesException( e.getCause() );
    }
    catch( final Throwable t )
    {
      invocation.assertMatchesException( t );
    }
    if( completed )
    {
      Assert.assertTrue( invocation.isInvoked(), "invocation.isInvoked()" );
    }
  }

  @DataProvider( name = "scenarios" )
  public Object[][] createTestInvocations()
  {
    final ArrayList<TestInvocation> tests = new ArrayList<TestInvocation>();

    final Object[] parameters = new Object[0];

    addTestSet( tests, TestInvocation.STATIC, parameters );
    addTestSet( tests, TestInvocation.INSTANCE, parameters );

    final Object[][] results = new Object[tests.size()][];
    for( int i = 0; i < results.length; i++ )
    {
      results[i] = new Object[]{ tests.get( i ) };
    }
    return results;
  }

  private void addTestSet( final ArrayList<TestInvocation> tests, final int methodType, final Object[] parameters )
  {
    failInEDT( tests, DisallowsEDT.class, methodType, parameters );
    succeed( tests, DisallowsEDT.class, methodType, false, parameters, false );
    failOutsideEDT( tests, RequiresEDT.class, methodType, parameters );
    succeed( tests, RequiresEDT.class, methodType, true, parameters, true );
    succeed( tests, RunInEDT.class, methodType, true, parameters, true );
    succeed( tests, RunInEDT.class, methodType, false, parameters, true );
    succeed( tests, RunOutsideEDT.class, methodType, true, parameters, false );
    succeed( tests, RunOutsideEDT.class, methodType, false, parameters, false );
  }

  private static void succeed( final ArrayList<TestInvocation> tests,
                               final Class<?> annotation,
                               final int methodType,
                               final boolean inEDT,
                               final Object[] parameters,
                               final boolean expectedInEDT )
  {
    ti( tests, annotation, methodType, inEDT, parameters, expectedInEDT, null, null );
  }

  private static void failInEDT( final ArrayList<TestInvocation> tests,
                                 final Class<?> annotation,
                                 final int methodType,
                                 final Object[] parameters )
  {

    final String message =
      "Method " + TestInvocation.METHOD_NAME + " must only be invoked in the Event Dispatch Thread.";
    ti( tests, annotation, methodType, true, parameters, true, IllegalStateException.class, message );
  }

  private static void failOutsideEDT( final ArrayList<TestInvocation> tests,
                                      final Class<?> annotation,
                                      final int methodType,
                                      final Object[] parameters )
  {

    final String message =
      "Method " + TestInvocation.METHOD_NAME + " must not be invoked in the Event Dispatch Thread.";
    ti( tests, annotation, methodType, false, parameters, false, IllegalStateException.class, message );
  }

  private static void ti( final ArrayList<TestInvocation> tests,
                          final Class<?> annotation,
                          final int methodType,
                          final boolean inEDT,
                          final Object[] parameters,
                          final boolean expectedInEDT,
                          final Class<? extends Throwable> expectedExceptionType,
                          final String expectedExceptionMessage )
  {
    tests.add( new TestInvocation( annotation,
                                   methodType,
                                   inEDT,
                                   parameters,
                                   expectedInEDT,
                                   expectedExceptionType,
                                   expectedExceptionMessage ) );
  }
}
