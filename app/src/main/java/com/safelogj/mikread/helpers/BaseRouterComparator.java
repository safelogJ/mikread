package com.safelogj.mikread.helpers;

import com.safelogj.mikread.MikrotikRouter;

import java.util.Comparator;

public abstract class BaseRouterComparator implements Comparator<MikrotikRouter> {
    protected boolean isReverse;

    public void toggleOrder() {
        isReverse = !isReverse;
    }

    protected int applyOrder(int result) {
        return isReverse ? -result : result;
    }
}
