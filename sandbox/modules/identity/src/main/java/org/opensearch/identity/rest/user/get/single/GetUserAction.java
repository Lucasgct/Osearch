/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.identity.rest.user.get.single;

import org.opensearch.action.ActionType;

/**
 * Action type for getting a user
 */
public class GetUserAction extends ActionType<GetUserResponse> {

    public static final GetUserAction INSTANCE = new GetUserAction();

    // TODO : revisit this action type
    public static final String NAME = "cluster:admin/user/get";

    private GetUserAction() {
        super(NAME, GetUserResponse::new);
    }
}
