package org.jasm.item.constantpool;

public class MethodrefInfo extends AbstractRefInfo {

	@Override
	public short getTag() {
		return 10;
	}

	@Override
	public String getPrintName() {
		return "methodrefinfo";
	}
	
	

}
