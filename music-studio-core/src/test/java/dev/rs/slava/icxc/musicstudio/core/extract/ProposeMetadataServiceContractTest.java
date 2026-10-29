package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.testkit.contract.ProposeMetadataUseCaseContract;

/** X02: the core service fulfils the use case contract. */
class ProposeMetadataServiceContractTest extends ProposeMetadataUseCaseContract {

    @Override
    protected ProposeMetadataUseCase create() {
        return new ProposeMetadataService();
    }
}
