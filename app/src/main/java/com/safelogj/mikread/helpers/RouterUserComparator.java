package com.safelogj.mikread.helpers;

import com.safelogj.mikread.MikrotikRouter;

public class RouterUserComparator extends BaseRouterComparator {

    @Override
    public int compare(MikrotikRouter r1, MikrotikRouter r2) {
        return applyOrder(r1.getUser().compareToIgnoreCase(r2.getUser()));
    }
}
