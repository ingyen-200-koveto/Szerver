package hu.icemine.core.board;

public final class TabListAccess {

    private TabListAccess() {
    }

    public static String sortKey(int order, String name) {
        return TabList.sortKey(order, name);
    }
}
