package com.vectras.vm;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

public class VMManagerDeleteVmGuardsTest {

    @Test
    public void parseVmListJson_invalidJson_returnsEmptyList() {
        ArrayList<HashMap<String, Object>> vmList = VMManager.parseVmListJson("{invalid-json");

        Assert.assertNotNull(vmList);
        Assert.assertTrue(vmList.isEmpty());
    }

    @Test
    public void isValidVmPosition_emptyList_returnsFalse() {
        ArrayList<HashMap<String, Object>> vmList = new ArrayList<>();

        Assert.assertFalse(VMManager.isValidVmPosition(vmList, 0));
    }

    @Test
    public void isValidVmPosition_negativePosition_returnsFalse() {
        ArrayList<HashMap<String, Object>> vmList = new ArrayList<>();
        vmList.add(new HashMap<>());

        Assert.assertFalse(VMManager.isValidVmPosition(vmList, -1));
    }

    @Test
    public void isValidVmPosition_positionEqualToSize_returnsFalse() {
        ArrayList<HashMap<String, Object>> vmList = new ArrayList<>();
        vmList.add(new HashMap<>());

        Assert.assertFalse(VMManager.isValidVmPosition(vmList, 1));
    }
}
