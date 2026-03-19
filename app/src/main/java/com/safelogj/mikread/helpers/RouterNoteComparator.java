package com.safelogj.mikread.helpers;

import com.safelogj.mikread.MikrotikRouter;

public class RouterNoteComparator extends BaseRouterComparator {
    @Override
    public int compare(MikrotikRouter r1, MikrotikRouter r2) {
        return applyOrder(r1.getNote().compareToIgnoreCase(r2.getNote()));
    }
}

