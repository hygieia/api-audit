package com.capitalone.dashboard.common;

import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.util.ConversionUtils;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;

public class ConversionUtilsTest {

    @Test
    public void testMatchIdentifier(){

        Assert.assertTrue(ConversionUtils.matchAltIdentifier(makeCollectorItem("https://github.com/test-org/test-project/tree/master"),"https://github.com/test-org/test-project/tree/master"));
        Assert.assertTrue(ConversionUtils.matchAltIdentifier(makeCollectorItem("https://github.com/test-org/test-project/tree/master"),"https://github.com/test-org/Test-project/tree/master"));
        Assert.assertTrue(ConversionUtils.matchAltIdentifier(makeCollectorItem("https://github.test.com/test-org/test-project/tree/master"),"https://github.test1.com/test-org/Test-project/tree/master"));
        Assert.assertTrue(ConversionUtils.matchAltIdentifier(makeCollectorItem("https://github1.test.com/test-org/test-project/tree/master"),"https://github.com/test-org/Test-project/tree/master"));
        Assert.assertFalse(ConversionUtils.matchAltIdentifier(makeCollectorItem("https://github.com/test-org/test-project/tree/master"),"https://github.com/test-org/test-project"));
        Assert.assertFalse(ConversionUtils.matchAltIdentifier(makeCollectorItem(null),"https://github.com/test-org/test-project"));
        Assert.assertFalse(ConversionUtils.matchAltIdentifier(makeCollectorItem(null),null));

    }

    private CollectorItem makeCollectorItem(String altIdentifier) {
        CollectorItem item = new CollectorItem();
        item.setCollectorId(ObjectId.get());
        item.setEnabled(true);
        item.setPushed(true);
        item.getOptions().put("url", "http://github.com/capone/hygieia");
        item.getOptions().put("branch", "branch");
        item.setAltIdentifier(altIdentifier);
        return item;
    }

}
