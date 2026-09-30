/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.action.start.validation;

import com.atlassian.opensearch.aosc.model.MigrationRequestOptions;

import org.opensearch.Version;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Unit tests for {@link DataLossConsentValidator}.
 */
public class DataLossConsentValidatorTests extends OpenSearchTestCase {

    private static IndexMetadata buildMeta(String name, int shards) {
        return IndexMetadata.builder(name)
            .settings(
                Settings.builder()
                    .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
                    .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, shards)
                    .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            )
            .build();
    }

    public void testRejectsBulkApiWithoutConsent() {
        IllegalArgumentException ex = DataLossConsentValidator.checkDataLossConsent(buildMeta("src", 2), buildMeta("tgt", 3), null);
        assertNotNull(ex);
        assertTrue(ex.getMessage(), ex.getMessage().contains("accept_data_loss_if_custom_routing_is_used=true"));
    }

    public void testAllowsBulkApiWithConsent() {
        MigrationRequestOptions opts = new MigrationRequestOptions().setAcceptDataLossIfCustomRoutingIsUsed(true);
        assertNull(DataLossConsentValidator.checkDataLossConsent(buildMeta("src", 2), buildMeta("tgt", 3), opts));
    }

    public void testAllowsSameShard() {
        assertNull(DataLossConsentValidator.checkDataLossConsent(buildMeta("src", 3), buildMeta("tgt", 3), null));
    }
}
