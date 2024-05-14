/*!
 *
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 *
 * Copyright (c) 2002-2024 Hitachi Vantara. All rights reserved.
 *
 */

package org.pentaho.platform.scheduler2.ws;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.IAuthorizationPolicy;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.scheduler2.ComplexJobTrigger;
import org.pentaho.platform.api.scheduler2.IBlockoutManager;
import org.pentaho.platform.api.scheduler2.IJob;
import org.pentaho.platform.api.scheduler2.IScheduler;
import org.pentaho.platform.api.scheduler2.Job;
import org.pentaho.platform.api.scheduler2.JobTrigger;
import org.pentaho.platform.api.scheduler2.SchedulerException;
import org.pentaho.platform.api.scheduler2.SimpleJobTrigger;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.security.policy.rolebased.actions.SchedulerAction;
import org.pentaho.platform.security.policy.rolebased.actions.SchedulerExecuteAction;

import javax.jws.WebService;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * The default implementation of the {@link ISchedulerService} which acts as a proxy to the {@link IScheduler}
 *
 * @author aphillips
 */
@WebService( endpointInterface = "org.pentaho.platform.scheduler2.ws.ISchedulerService", name = "Scheduler",
  serviceName = "Scheduler", portName = "SchedulerPort", targetNamespace = "http://www.pentaho.org/ws/1.0" )
public class DefaultSchedulerService implements ISchedulerService {
  private static final String ADMIN_PERM = "org.pentaho.security.administerSecurity";
  private static final Log LOGGER = LogFactory.getLog( DefaultSchedulerService.class );
  private String defaultActionId; // for testing only

  private String getDefaultActionId() {
    return defaultActionId == null ? "PdiAction" : defaultActionId;
  }

  public void setDefaultActionId( String defaultActionId ) {
    this.defaultActionId = defaultActionId;
  }

  /**
   * {@inheritDoc}
   */
  public String createSimpleJob( String jobName, Map<String, ParamValue> jobParams, SimpleJobTrigger trigger )
    throws SchedulerException {
    return createJob( jobName, jobParams, trigger );
  }

  /**
   * {@inheritDoc}
   */
  public String createComplexJob( String jobName, Map<String, ParamValue> jobParams, ComplexJobTrigger trigger )
    throws SchedulerException {
    return createJob( jobName, jobParams, trigger );
  }

  private String createJob( String jobName, Map<String, ParamValue> jobParams, JobTrigger trigger )
    throws SchedulerException {

    LOGGER.debug( "Creating job with schedule " + trigger.toString() );

    IJob job;
    try {
      IScheduler scheduler = getScheduler2();
      Map<String, Serializable> properJobParams = toProperMap( jobParams );
      scheduler.validateJobParams( properJobParams );
      job = scheduler.createJob( jobName, getDefaultActionId(), properJobParams, trigger );
    } catch ( SchedulerException e ) {
      LOGGER.error( e.getMessage(), e ); // temporary error logging.. this needs to become an aspect
      throw e;
    }
    return job.getJobId();
  }

  private void updateJob( String jobId, Map<String, ParamValue> jobParams, JobTrigger trigger )
    throws SchedulerException {
    LOGGER.debug( "Creating job with schedule " + trigger.toString() );
    try {
      IScheduler scheduler = getScheduler2();
      Map<String, Serializable> properJobParams = toProperMap( jobParams );
      scheduler.updateJob( jobId, properJobParams, trigger );
    } catch ( SchedulerException e ) {
      LOGGER.error( e.getMessage(), e ); // temporary error logging.. this needs to become an aspect
      throw e;
    }
  }

  /**
   * {@inheritDoc}
   */
  public Job[] getJobs() throws SchedulerException {
    String principalName = getPentahoSession().getName();
    boolean canAdminister = getAuthorizationPolicy().isAllowed( ADMIN_PERM );
    boolean canExecuteScheduler = getAuthorizationPolicy().isAllowed( SchedulerExecuteAction.NAME );

    return getScheduler2().getJobs( job -> {
      if ( canAdminister || canExecuteScheduler ) {
        return !IBlockoutManager.BLOCK_OUT_JOB_NAME.equals( job.getJobName() );
      }
      return principalName.equals( job.getUserName() );
    } ).toArray( new Job[ 0 ] );
  }

  /**
   * {@inheritDoc}
   */
  public void pause() throws SchedulerException {
    IScheduler scheduler = getScheduler2();
    if ( canStopScheduler() ) {
      scheduler.pause();
    } else {
      throw new SchedulerException( "Operation not allowed" );
    }
  }

  /**
   * {@inheritDoc}
   */
  public void pauseJob( String jobId ) throws SchedulerException {
    IScheduler scheduler = getScheduler2();
    scheduler.pauseJob( jobId );
  }

  @Override
  public boolean canStopScheduler() {
    return getAuthorizationPolicy().isAllowed( ADMIN_PERM );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canSchedule() {
    return getAuthorizationPolicy().isAllowed( SchedulerAction.NAME );
  }

  /**
   * {@inheritDoc}
   */
  public void removeJob( String jobId ) throws SchedulerException {
    IScheduler scheduler = getScheduler2();
    scheduler.removeJob( jobId );
  }

  /**
   * {@inheritDoc}
   */
  public void start() throws SchedulerException {
    IScheduler scheduler = getScheduler2();
    scheduler.start();
  }

  /**
   * {@inheritDoc}
   */
  public void resumeJob( String jobId ) throws SchedulerException {
    IScheduler scheduler = getScheduler2();
    scheduler.resumeJob( jobId );
  }

  /**
   * {@inheritDoc}
   */
  public int getSchedulerStatus() throws SchedulerException {
    IScheduler scheduler = getScheduler2();
    return scheduler.getStatus().ordinal();
  }

  /**
   * {@inheritDoc}
   */
  public void updateJobToUseSimpleTrigger( String jobId, Map<String, ParamValue> jobParams, SimpleJobTrigger trigger )
    throws SchedulerException {
    updateJob( jobId, jobParams, trigger );
  }

  /**
   * {@inheritDoc}
   */
  public String updateJobSimpleTriggerWithJobName( String jobName, String jobId, Map<String, ParamValue> jobParams,
                                                   SimpleJobTrigger trigger )
    throws SchedulerException {
    String newJobId = createJob( jobName, jobParams, trigger );
    removeJob( jobId );
    return newJobId;
  }

  /**
   * {@inheritDoc}
   */
  public String updateJobComplexTriggerWithJobName( String jobName, String jobId, Map<String, ParamValue> jobParams,
                                                    ComplexJobTrigger trigger )
    throws SchedulerException {
    String newJobId = createJob( jobName, jobParams, trigger );
    removeJob( jobId );
    return newJobId;
  }

  /**
   * {@inheritDoc}
   */
  public void updateJobToUseComplexTrigger( String jobId, Map<String, ParamValue> jobParams, ComplexJobTrigger trigger )
    throws SchedulerException {
    updateJob( jobId, jobParams, trigger );
  }

  /**
   * Helper function that calls {@link PentahoSystem#get(Class, String, IPentahoSession)}
   */
  @VisibleForTesting
  IScheduler getScheduler2() {
    return PentahoSystem.get( IScheduler.class, "IScheduler2", null );
  }

  /**
   * Helper function that calls {@link PentahoSessionHolder#getSession()}
   */
  @VisibleForTesting
  IPentahoSession getPentahoSession() {
    return PentahoSessionHolder.getSession();
  }

  /**
   * Helper function that calls {@link PentahoSystem#get(Class)}
   */
  @VisibleForTesting
  IAuthorizationPolicy getAuthorizationPolicy() {
    return PentahoSystem.get( IAuthorizationPolicy.class );
  }

  private Map<String, Serializable> toProperMap( Map<String, ParamValue> liteMap ) {
    Map<String, Serializable> ret = new HashMap<>();
    for ( Map.Entry<String, ParamValue> entry : liteMap.entrySet() ) {
      ParamValue val = entry.getValue();
      if ( val instanceof StringParamValue ) {
        ret.put( entry.getKey(), val.toString() );
      } else {
        ret.put( entry.getKey(), (Serializable) val );
      }
    }
    return ret;
  }

}
