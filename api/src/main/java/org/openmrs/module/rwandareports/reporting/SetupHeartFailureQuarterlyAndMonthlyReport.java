/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandareports.reporting;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.openmrs.Concept;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Program;
import org.openmrs.api.PatientSetService.TimeModifier;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.cohort.definition.CompositionCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.EncounterCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.NumericObsCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.ProgramEnrollmentCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.SqlCohortDefinition;
import org.openmrs.module.reporting.common.RangeComparator;
import org.openmrs.module.reporting.dataset.definition.CohortIndicatorDataSetDefinition;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.evaluation.parameter.ParameterizableUtil;
import org.openmrs.module.reporting.indicator.CohortIndicator;
import org.openmrs.module.reporting.query.encounter.definition.EncounterQuery;
import org.openmrs.module.reporting.query.encounter.definition.SqlEncounterQuery;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.rwandareports.dataset.EncounterIndicatorDataSetDefinition;
import org.openmrs.module.rwandareports.dataset.LocationHierachyIndicatorDataSetDefinition;
import org.openmrs.module.rwandareports.indicator.EncounterIndicator;
import org.openmrs.module.rwandareports.util.Cohorts;
import org.openmrs.module.rwandareports.util.GlobalPropertiesManagement;
import org.openmrs.module.rwandareports.util.Indicators;
import org.openmrs.module.rwandareports.widget.AllLocation;
import org.openmrs.module.rwandareports.widget.LocationHierarchy;

/**
 *
 */
public class SetupHeartFailureQuarterlyAndMonthlyReport {
	
	public Helper h = new Helper();
	
	GlobalPropertiesManagement gp = new GlobalPropertiesManagement();
	
	// properties
	private Program heartFailureProgram;
	
	private List<Program> HFPrograms = new ArrayList<Program>();
	
	List<EncounterType> heartFailureEncounterTypes;
	
	private List<String> onOrAfterOnOrBefore = new ArrayList<String>();
	
	private Form heartFailureDDBform;
	
    private Concept NYHACLASS;
    
    private Concept NYHACLASS4;
	
	private Concept hypertensiveDisease;  
	
	private Concept serumCreatinine;
	
	public void setup() throws Exception {
		
		setUpProperties();
		
		// Monthly report set-up
		ReportDefinition monthlyRd = new ReportDefinition();
		monthlyRd.addParameter(new Parameter("startDate", "Start Date", Date.class));
		monthlyRd.addParameter(new Parameter("endDate", "End Date", Date.class));
		
		Properties properties = new Properties();
		properties.setProperty("hierarchyFields", "countyDistrict:District");
		monthlyRd.addParameter(new Parameter("location", "Location", AllLocation.class, properties));
		
		monthlyRd.setName("NCD-Heart Failure Indicator Report-Monthly");
		
		monthlyRd.addDataSetDefinition(createMonthlyLocationDataSet(),
		    ParameterizableUtil.createParameterMappings("startDate=${startDate},endDate=${endDate},location=${location}"));
		
		ProgramEnrollmentCohortDefinition patientEnrolledInHF = new ProgramEnrollmentCohortDefinition();
		patientEnrolledInHF.addParameter(new Parameter("enrolledOnOrBefore", "enrolledOnOrBefore", Date.class));
		patientEnrolledInHF.setPrograms(HFPrograms);
		
		monthlyRd.setBaseCohortDefinition(patientEnrolledInHF,
		    ParameterizableUtil.createParameterMappings("enrolledOnOrBefore=${endDate}"));
		
		h.saveReportDefinition(monthlyRd);
		
		ReportDesign monthlyDesign = h.createRowPerPatientXlsOverviewReportDesign(monthlyRd,
		    "HF_Monthly_Indicator_Report.xls", "Heart Failure Indicator Monthly Report (Excel)", null);
		Properties monthlyProps = new Properties();
		monthlyProps.put("repeatingSections", "sheet:1,dataset:Encounter Monthly Data Set");
		
		monthlyDesign.setProperties(monthlyProps);
		h.saveReportDesign(monthlyDesign);
		
	}
	
	public void delete() {
		ReportService rs = Context.getService(ReportService.class);
		for (ReportDesign rd : rs.getAllReportDesigns(false)) {
			if ("Heart Failure Indicator Monthly Report (Excel)".equals(rd.getName())) {
				rs.purgeReportDesign(rd);
			}
		}
		h.purgeReportDefinition("NCD-Heart Failure Indicator Report-Monthly");
		
	}
	
	private void createMonthlyIndicators(EncounterIndicatorDataSetDefinition dsd) {
		
		SqlEncounterQuery patientVisitsToHFClinic = new SqlEncounterQuery();
		
		StringBuilder query = new StringBuilder(
		        "select encounter_id from encounter where encounter_id in(select encounter_id from encounter where (encounter_type in (");
		int i = 0;
		for (EncounterType et : heartFailureEncounterTypes) {
			if (i > 0) {
				query.append(",");
			}
			query.append(et.getEncounterTypeId());
			i++;
		}
		query.append(")) and encounter_datetime>= :startDate and encounter_datetime<= :endDate and voided=0 group by encounter_datetime, patient_id)");
		
		patientVisitsToHFClinic.setQuery(query.toString());
		patientVisitsToHFClinic.setName("patientVisitsToHFClinic");
		patientVisitsToHFClinic.addParameter(new Parameter("startDate", "startDate", Date.class));
		patientVisitsToHFClinic.addParameter(new Parameter("endDate", "endDate", Date.class));
		
		EncounterIndicator patientVisitsToHFClinicMonthIndicator = new EncounterIndicator();
		patientVisitsToHFClinicMonthIndicator.setName("patientVisitsToHFClinicMonthIndicator");
		patientVisitsToHFClinicMonthIndicator.setEncounterQuery(new Mapped<EncounterQuery>(patientVisitsToHFClinic,
		        ParameterizableUtil.createParameterMappings("endDate=${endDate},startDate=${endDate-1m+1d}")));
		
		dsd.addColumn(patientVisitsToHFClinicMonthIndicator);
		
	}
	
	// Create Monthly Location Data set
	public LocationHierachyIndicatorDataSetDefinition createMonthlyLocationDataSet() {
		
		LocationHierachyIndicatorDataSetDefinition ldsd = new LocationHierachyIndicatorDataSetDefinition(
		        createMonthlyEncounterBaseDataSet());
		ldsd.addBaseDefinition(createMonthlyBaseDataSet());
		ldsd.setName("Encounter Monthly Data Set");
		ldsd.addParameter(new Parameter("startDate", "Start Date", Date.class));
		ldsd.addParameter(new Parameter("endDate", "End Date", Date.class));
		ldsd.addParameter(new Parameter("location", "District", LocationHierarchy.class));
		
		return ldsd;
	}
	
	private EncounterIndicatorDataSetDefinition createMonthlyEncounterBaseDataSet() {
		
		EncounterIndicatorDataSetDefinition eidsd = new EncounterIndicatorDataSetDefinition();
		
		eidsd.setName("eidsd");
		eidsd.addParameter(new Parameter("startDate", "Start Date", Date.class));
		eidsd.addParameter(new Parameter("endDate", "End Date", Date.class));
		
		createMonthlyIndicators(eidsd);
		return eidsd;
	}
	
	// create monthly cohort Data set
	
	private CohortIndicatorDataSetDefinition createMonthlyBaseDataSet() {
		CohortIndicatorDataSetDefinition dsd = new CohortIndicatorDataSetDefinition();
		dsd.setName("Monthly Cohort Data Set");
		dsd.addParameter(new Parameter("startDate", "Start Date", Date.class));
		dsd.addParameter(new Parameter("endDate", "End Date", Date.class));
		
		createMonthlyIndicators(dsd);
		return dsd;
	}
	
	private void createMonthlyIndicators(CohortIndicatorDataSetDefinition dsd) {
		// =======================================================
		// A2: Total # of patients seen in the last month
		// =======================================================
		EncounterCohortDefinition patientSeen = Cohorts.createEncounterParameterizedByDate("Patients seen",
		    onOrAfterOnOrBefore, heartFailureEncounterTypes);
		
		CohortIndicator patientsSeenIndicator = Indicators.newCountIndicator("patientsSeenIndicator", patientSeen,
		    ParameterizableUtil.createParameterMappings("onOrAfter=${startDate},onOrBefore=${endDate}"));
		
		dsd.addColumn("A2M", "Total # of patients seen in the last month", new Mapped(patientsSeenIndicator,
		        ParameterizableUtil.createParameterMappings("startDate=${startDate},endDate=${endDate}")), "");
		
		//=======================================================
		// A3: Total # of new patients enrolled in the last month
		//=======================================================
		ProgramEnrollmentCohortDefinition enrolledInHFProgram = Cohorts
		        .createProgramEnrollmentParameterizedByStartEndDate("enrolledInHFProgram", heartFailureProgram);
		
		CohortIndicator enrolledInHFProgramIndicator = Indicators.newCountIndicator(
		    "enrolledInHFProgramIndicator", enrolledInHFProgram,
		    ParameterizableUtil.createParameterMappings("enrolledOnOrAfter=${startDate},enrolledOnOrBefore=${endDate}"));
		
		dsd.addColumn(
		    "A3M",
		    "Total # of new patients enrolled in the last month",
		    new Mapped(enrolledInHFProgramIndicator, ParameterizableUtil
		            .createParameterMappings("startDate=${startDate},endDate=${endDate}")), "");
		
		//=======================================================
		// B4: Of the new patients enrolled in the last month, % with NYHA class IV 
		//=======================================================
		
		SqlCohortDefinition patientsWithNYHAClassIV = Cohorts
        .getPatientsWithObservationInFormBetweenStartAndEndDate("patientsWithpatientsWithNYHAClassIV",
        	heartFailureDDBform, NYHACLASS, NYHACLASS4);
		
		CompositionCohortDefinition patientsEnrolledAndWithNYHAClassIV = new CompositionCohortDefinition();
		patientsEnrolledAndWithNYHAClassIV
		        .setName("patientsEnrolledAndWithNYHAClassIV");
		patientsEnrolledAndWithNYHAClassIV.addParameter(new Parameter("onOrAfter", "onOrAfter",
		        Date.class));
		patientsEnrolledAndWithNYHAClassIV.addParameter(new Parameter("onOrBefore", "onOrBefore",
		        Date.class));

		patientsEnrolledAndWithNYHAClassIV.addParameter(new Parameter("startDate", "startDate",
		        Date.class));
		patientsEnrolledAndWithNYHAClassIV.addParameter(new Parameter("endDate", "endDate", Date.class));
		patientsEnrolledAndWithNYHAClassIV.getSearches().put(
		    "1",
		    new Mapped<CohortDefinition>(patientsWithNYHAClassIV, ParameterizableUtil
		            .createParameterMappings("startDate=${startDate},endDate=${endDate}")));
		patientsEnrolledAndWithNYHAClassIV.getSearches().put(
		    "2",
		    new Mapped<CohortDefinition>(enrolledInHFProgram, ParameterizableUtil
		            .createParameterMappings("enrolledOnOrAfter=${onOrAfter},enrolledOnOrBefore=${onOrBefore}")));
		patientsEnrolledAndWithNYHAClassIV.setCompositionString("1 AND 2");
		
		CohortIndicator patientsEnrolledAndWithNYHAClassIVIndicator = Indicators
		        .newCountIndicator(
		            "patientsEnrolledAndWithNYHAClassIVIndicator",
		            patientsEnrolledAndWithNYHAClassIV,
		            ParameterizableUtil
		                    .createParameterMappings("endDate=${endDate},startDate=${startDate},onOrBefore=${endDate},onOrAfter=${startDate}"));
		dsd.addColumn(
		    "B4M",
		    "New patients enrolled in the last month with NYHA class IV ",
		    new Mapped(patientsEnrolledAndWithNYHAClassIVIndicator, ParameterizableUtil
		            .createParameterMappings("startDate=${startDate},endDate=${endDate}")), "");
		
		//=======================================================
		// B5: Of the patients newly enrolled in month with documented creatinine check in the last month, # and % with first creatinine >200
		
		//=======================================================
		NumericObsCohortDefinition patientsWithCRGreaterThan200 = Cohorts.createNumericObsCohortDefinition(
		    "patientsWithCRGreaterThan200", onOrAfterOnOrBefore, serumCreatinine, 200,
		    RangeComparator.GREATER_THAN, TimeModifier.FIRST);
		
		CompositionCohortDefinition enrolledWithWithCRGreaterThan200 = new CompositionCohortDefinition();
		enrolledWithWithCRGreaterThan200.setName("enrolledWithWithCRGreaterThan200");
		enrolledWithWithCRGreaterThan200.addParameter(new Parameter("onOrAfter", "onOrAfter", Date.class));
		enrolledWithWithCRGreaterThan200.addParameter(new Parameter("onOrBefore", "onOrBefore", Date.class));
		enrolledWithWithCRGreaterThan200.getSearches().put(
		    "1",
		    new Mapped<CohortDefinition>(patientsWithCRGreaterThan200, ParameterizableUtil
		            .createParameterMappings("onOrAfter=${onOrAfter},onOrBefore=${onOrBefore}")));
		enrolledWithWithCRGreaterThan200.getSearches().put(
		    "2",
		    new Mapped<CohortDefinition>(enrolledInHFProgram, ParameterizableUtil
		            .createParameterMappings("enrolledOnOrAfter=${onOrAfter},enrolledOnOrBefore=${onOrBefore}")));
		enrolledWithWithCRGreaterThan200
		        .setCompositionString("1 AND 2");
		
		CohortIndicator enrolledWithCRGreaterThan200Indicator = Indicators
		        .newCountIndicator(
		            "enrolledWithWithCRGreaterThan200Indicator",
		            enrolledWithWithCRGreaterThan200,
		            ParameterizableUtil
		                    .createParameterMappings("onOrAfter=${startDate},onOrBefore=${endDate}"));
		
		dsd.addColumn(
		    "B5M",
		    "Newly enrolled in month with documented creatinine check in the last month, with first creatinine >200",
		    new Mapped(enrolledWithCRGreaterThan200Indicator, ParameterizableUtil
		            .createParameterMappings("startDate=${startDate},endDate=${endDate}")), "");
		           
		
	}
	
	private void setUpProperties() {
		heartFailureProgram = gp.getProgram(GlobalPropertiesManagement.HEART_FAILURE_PROGRAM_NAME);
		HFPrograms.add(heartFailureProgram);
		
		heartFailureEncounterTypes = gp.getEncounterTypeList(GlobalPropertiesManagement.CARDIOLOGY_ENCTOUNTER_TYPES);
		
		onOrAfterOnOrBefore.add("onOrAfter");
		onOrAfterOnOrBefore.add("onOrBefore");		
		heartFailureDDBform = gp.getForm(GlobalPropertiesManagement.HEART_FAILURE_DDB);		
	    NYHACLASS = gp.getConcept(GlobalPropertiesManagement.NYHA_CLASS);
	    NYHACLASS4 =gp.getConcept(GlobalPropertiesManagement.NYHA_CLASS_4);
	    serumCreatinine = gp.getConcept(GlobalPropertiesManagement.SERUM_CREATININE);
		
	}
	
}
