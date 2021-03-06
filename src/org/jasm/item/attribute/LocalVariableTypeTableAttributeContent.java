package org.jasm.item.attribute;

import org.jasm.bytebuffer.IByteBuffer;
import org.jasm.environment.Environment;
import org.jasm.item.AbstractBytecodeItemList;

public class LocalVariableTypeTableAttributeContent extends
		AbstractBytecodeItemList<DebugLocalVariableType> implements IAttributeContent {

	@Override
	public String getPrintName() {
		return "debug var types";
	}
	
	@Override
	public void prepareRead(int length) {

	}

	@Override
	protected DebugLocalVariableType createEmptyItem(IByteBuffer source, long offset) {
		return new DebugLocalVariableType();
	}
	
	@Override
	public boolean toOmit() {
		boolean omitDebugInfos = Environment.getBooleanValue("jdasm.omitdebuginfos");
		return omitDebugInfos;
	}

}
