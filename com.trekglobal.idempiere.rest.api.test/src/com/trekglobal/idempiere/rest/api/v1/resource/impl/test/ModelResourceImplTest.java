/******************************************************************************
 * Project: Trek Global ERP                                                   *
 * Copyright (C) Trek Global Corporation                			          *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *                                                                            *
 * Contributors:                                                              *
 * - Elaine Tan                                                               *
 *****************************************************************************/
package com.trekglobal.idempiere.rest.api.v1.resource.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import javax.ws.rs.core.Response;

import org.compiere.model.MChangeLog;
import org.compiere.model.MSession;
import org.compiere.model.MSysConfig;
import org.compiere.model.MTest;
import org.compiere.model.Query;
import org.compiere.util.Env;
import org.idempiere.tracking.AuditTraceContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trekglobal.idempiere.rest.api.json.test.RestTestCase;
import com.trekglobal.idempiere.rest.api.v1.resource.impl.ModelResourceImpl;

public class ModelResourceImplTest extends RestTestCase {

	@Test
	void testCRUDWithExternalTraceId() {
		ModelResourceImpl modelResource = new ModelResourceImpl();
		
		MSession.create(Env.getCtx());
		String externalTraceId = UUID.randomUUID().toString();
		AuditTraceContext.setExternalTraceId(externalTraceId);
		try (MockedStatic<MSysConfig> mocked = Mockito.mockStatic(MSysConfig.class, Mockito.CALLS_REAL_METHODS)) {
			mocked.when(() -> MSysConfig.getValue(MSysConfig.SYSTEM_INSERT_CHANGELOG, "N", getAD_Client_ID()))
			.thenReturn("K");
			
			String tableName = MTest.Table_Name;
			String jsonText = "{\"Name\": \"t1_"+System.currentTimeMillis() + "\" }";
			Response response = modelResource.create(tableName, jsonText);
			assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
			String jsonString = response.getEntity().toString();
			JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
			int id = jsonObject.getAsJsonObject().get("id").getAsInt();
			assertTrue(id > 0, "Failed to create record");
			Query query = new Query(Env.getCtx(), MChangeLog.Table_Name, 
					MChangeLog.COLUMNNAME_AD_Table_ID + "=? AND " 
							+ MChangeLog.COLUMNNAME_Record_ID + "=? AND "
							+ MChangeLog.COLUMNNAME_EventChangeLog + "=?", getTrxName());
			MChangeLog changeLog = query.setParameters(MTest.Table_ID, id, MChangeLog.EVENTCHANGELOG_Insert).first();
			assertNotNull(changeLog, "No change log found for inserted record");
			assertTrue(changeLog.get_ID() > 0, "Change log ID is invalid");
			assertEquals(externalTraceId, changeLog.getExternalTraceId(), "Unexpected ExternalTraceId");
			
			jsonText = "{\"Name\": \"Test_"+System.currentTimeMillis() + "\" }";
			response = modelResource.update(tableName, String.valueOf(id), jsonText);
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			changeLog = query.setParameters(MTest.Table_ID, id, MChangeLog.EVENTCHANGELOG_Update).first();
			assertNotNull(changeLog, "No change log found for updated record");
			assertEquals(externalTraceId, changeLog.getExternalTraceId(), "Unexpected ExternalTraceId");
			
			response = modelResource.delete(tableName, String.valueOf(id));
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			changeLog = query.setParameters(MTest.Table_ID, id, MChangeLog.EVENTCHANGELOG_Delete).first();
			assertNotNull(changeLog, "No change log found for deleted record");
			assertEquals(externalTraceId, changeLog.getExternalTraceId(), "Unexpected ExternalTraceId");
		} finally {
			AuditTraceContext.clear();
		}
		
	}

}