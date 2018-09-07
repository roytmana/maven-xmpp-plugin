package net.alterasoft.maven.xmpp;


import org.apache.maven.plugin.testing.MojoRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XmppSendMojoTest {
  @Rule
  public MojoRule rule = new MojoRule() {
    @Override
    protected void before() throws Throwable {
    }

    @Override
    protected void after() {
    }
  };

  @Test
  public void testSomething() throws Exception {
    File pom = new File("target/test-classes/project-to-test/");
    assertNotNull(pom);
    assertTrue(pom.exists());

    XmppSendMojo xmppSendMojo = (XmppSendMojo)rule.lookupConfiguredMojo(pom, "send");
    assertNotNull(xmppSendMojo);
    xmppSendMojo.execute();
    assertTrue(true);

/*
        File outputDirectory = ( File ) rule.getVariableValueFromObject(xmppSendMojo, "outputDirectory" );
        assertNotNull( outputDirectory );
        assertTrue( outputDirectory.exists() );

        File touch = new File( outputDirectory, "touch.txt" );
        assertTrue( touch.exists() );
*/

  }

}

