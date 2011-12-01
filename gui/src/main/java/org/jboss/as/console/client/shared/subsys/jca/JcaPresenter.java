package org.jboss.as.console.client.shared.subsys.jca;

import com.google.gwt.event.shared.EventBus;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.Proxy;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.dispatch.DispatchAsync;
import org.jboss.as.console.client.shared.dispatch.impl.DMRAction;
import org.jboss.as.console.client.shared.dispatch.impl.DMRResponse;
import org.jboss.as.console.client.shared.subsys.Baseadress;
import org.jboss.as.console.client.shared.subsys.RevealStrategy;
import org.jboss.as.console.client.shared.subsys.jca.model.JcaArchiveValidation;
import org.jboss.as.console.client.shared.subsys.jca.model.JcaBootstrapContext;
import org.jboss.as.console.client.shared.subsys.jca.model.JcaConnectionManager;
import org.jboss.as.console.client.shared.subsys.jca.model.JcaWorkmanager;
import org.jboss.as.console.client.shared.viewframework.builder.ModalWindowLayout;
import org.jboss.as.console.client.widgets.forms.ApplicationMetaData;
import org.jboss.as.console.client.widgets.forms.BeanMetaData;
import org.jboss.as.console.client.widgets.forms.EntityAdapter;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * @author Heiko Braun
 * @date 11/29/11
 */
public class JcaPresenter extends Presenter<JcaPresenter.MyView, JcaPresenter.MyProxy> {

    private final PlaceManager placeManager;

    private RevealStrategy revealStrategy;
    private ApplicationMetaData metaData;
    private DispatchAsync dispatcher;

    private BeanMetaData beanMetaData;
    private BeanFactory factory;

    private EntityAdapter<JcaBootstrapContext> boostrapAdapter;
    private EntityAdapter<JcaBeanValidation> beanAdapter;
    private EntityAdapter<JcaArchiveValidation> archiveAdapter;
    private EntityAdapter<JcaConnectionManager> ccmAdapter;
    private EntityAdapter<JcaWorkmanager> managerAdapter;

    private LoadWorkmanagerCmd loadWorkManager;
    private DefaultWindow window;
    private DefaultWindow propertyWindow;
    private List<JcaWorkmanager> managers;



    @ProxyCodeSplit
    @NameToken(NameTokens.JcaPresenter)
    public interface MyProxy extends Proxy<JcaPresenter>, Place {
    }

    public interface MyView extends View {
        void setPresenter(JcaPresenter presenter);
        void setWorkManagers(List<JcaWorkmanager> managers);
        void setBeanSettings(JcaBeanValidation jcaBeanValidation);
        void setArchiveSettings(JcaArchiveValidation jcaArchiveValidation);
        void setCCMSettings(JcaConnectionManager jcaConnectionManager);
        void setBootstrapContexts(List<JcaBootstrapContext> contexts);
    }

    @Inject
    public JcaPresenter(
            EventBus eventBus, MyView view, MyProxy proxy,
            PlaceManager placeManager,
            DispatchAsync dispatcher,
            RevealStrategy revealStrategy,
            ApplicationMetaData metaData, BeanFactory factory) {
        super(eventBus, view, proxy);

        this.placeManager = placeManager;

        this.revealStrategy = revealStrategy;
        this.metaData = metaData;
        this.dispatcher = dispatcher;

        this.beanMetaData = metaData.getBeanMetaData(JcaWorkmanager.class);
        this.boostrapAdapter = new EntityAdapter<JcaBootstrapContext>(JcaBootstrapContext.class, metaData);

        this.managerAdapter= new EntityAdapter<JcaWorkmanager>(JcaWorkmanager.class, metaData);
        this.beanAdapter = new EntityAdapter<JcaBeanValidation>(JcaBeanValidation.class, metaData);
        this.archiveAdapter = new EntityAdapter<JcaArchiveValidation>(JcaArchiveValidation.class, metaData);
        this.ccmAdapter = new EntityAdapter<JcaConnectionManager>(JcaConnectionManager.class, metaData);

        this.factory = factory;
        this.loadWorkManager = new LoadWorkmanagerCmd(dispatcher, metaData);
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }


    @Override
    protected void onReset() {
        super.onReset();
        loadData();
    }

    private void loadData() {

        loadJcaSubsystem();
        loadWorkManager();
    }

    private void loadJcaSubsystem() {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(COMPOSITE);
        operation.get(ADDRESS).setEmptyList();

        ModelNode archive = new ModelNode();
        archive.get(ADDRESS).set(Baseadress.get());
        archive.get(ADDRESS).add("subsystem", "jca");
        archive.get(ADDRESS).add("archive-validation", "archive-validation");
        archive.get(OP).set(READ_RESOURCE_OPERATION);

        ModelNode bean = new ModelNode();
        bean.get(ADDRESS).set(Baseadress.get());
        bean.get(ADDRESS).add("subsystem", "jca");
        bean.get(ADDRESS).add("bean-validation", "bean-validation");
        bean.get(OP).set(READ_RESOURCE_OPERATION);

        ModelNode ccm = new ModelNode();
        ccm.get(ADDRESS).set(Baseadress.get());
        ccm.get(ADDRESS).add("subsystem", "jca");
        ccm.get(ADDRESS).add("cached-connection-manager", "cached-connection-manager");
        ccm.get(OP).set(READ_RESOURCE_OPERATION);

        List<ModelNode> steps = new ArrayList<ModelNode>(3);
        steps.add(archive);
        steps.add(bean);
        steps.add(ccm);

        operation.get(STEPS).set(steps);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                List<Property> steps = response.get(RESULT).asPropertyList();

                JcaArchiveValidation jcaArchiveValidation = archiveAdapter.fromDMR(
                        steps.get(0).getValue().get(RESULT).asObject()
                );
                JcaBeanValidation jcaBeanValidation = beanAdapter.fromDMR(
                        steps.get(1).getValue().get(RESULT).asObject()
                );
                JcaConnectionManager jcaConnectionManager = ccmAdapter.fromDMR(
                        steps.get(2).getValue().get(RESULT).asObject()
                );

                getView().setArchiveSettings(jcaArchiveValidation);
                getView().setBeanSettings(jcaBeanValidation);
                getView().setCCMSettings(jcaConnectionManager);
            }
        });
    }

    private void loadWorkManager() {
        loadWorkManager.execute(new SimpleCallback<List<JcaWorkmanager>>() {
            @Override
            public void onSuccess(List<JcaWorkmanager> result) {
                getView().setWorkManagers(result);
                JcaPresenter.this.managers = result;
                loadBootstrapContexts();
            }
        });
    }

    private void loadBootstrapContexts() {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "jca");
        operation.get(CHILD_TYPE).set("bootstrap-context");
        operation.get(RECURSIVE).set(true);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                List<Property> children = response.get(RESULT).asPropertyList();
                List<JcaBootstrapContext> contexts = new ArrayList<JcaBootstrapContext>(children.size());

                for(Property child : children)
                {
                    ModelNode value = child.getValue();
                    JcaBootstrapContext entity = boostrapAdapter.fromDMR(value);
                    contexts.add(entity);

                }

                getView().setBootstrapContexts(contexts);

            }
        });

    }

    @Override
    protected void revealInParent() {
        revealStrategy.revealInParent(this);
    }

    public PlaceManager getPlaceManager() {
        return placeManager;
    }

    public void onSaveArchiveSettings(Map<String, Object> changeset) {
        ModelNode address = new ModelNode();
        address.get(ADDRESS).set(Baseadress.get());
        address.get(ADDRESS).add("subsystem", "jca");
        address.get(ADDRESS).add("archive-validation", "archive-validation");
        ModelNode operation = archiveAdapter.fromChangeset(changeset, address);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to update JCA settings", response.getFailureDescription());
                else
                    Console.info("Success: Update JCA settings");

                loadJcaSubsystem();
            }
        });
    }

    public void onSaveBeanSettings(Map<String, Object> changeset) {
        ModelNode address = new ModelNode();
        address.get(ADDRESS).set(Baseadress.get());
        address.get(ADDRESS).add("subsystem", "jca");
        address.get(ADDRESS).add("bean-validation", "bean-validation");
        ModelNode operation = beanAdapter.fromChangeset(changeset, address);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to update JCA settings", response.getFailureDescription());
                else
                    Console.info("Success: Update JCA settings");

                loadJcaSubsystem();
            }
        });
    }

    public void onSaveCCMSettings(Map<String, Object> changeset) {
        ModelNode address = new ModelNode();
        address.get(ADDRESS).set(Baseadress.get());
        address.get(ADDRESS).add("subsystem", "jca");
        address.get(ADDRESS).add("cached-connection-manager", "cached-connection-manager");
        ModelNode operation = ccmAdapter.fromChangeset(changeset, address);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to update JCA settings", response.getFailureDescription());
                else
                    Console.info("Success: Update JCA settings");

                loadJcaSubsystem();
            }
        });
    }

    public void onSaveBootstrapContext(final JcaBootstrapContext entity, Map<String, Object> changeset) {
        ModelNode address = new ModelNode();
        address.get(ADDRESS).set(Baseadress.get());
        address.get(ADDRESS).add("subsystem", "jca");
        address.get(ADDRESS).add("bootstrap-context", entity.getName());
        ModelNode operation = boostrapAdapter.fromChangeset(changeset, address);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to update JCA settings", response.getFailureDescription());
                else
                    Console.info("Success: Update JCA settings");

                loadJcaSubsystem();
            }
        });
    }

    public void onDeleteBootstrapContext(final JcaBootstrapContext entity) {
        if(entity.getName().equals("default"))
        {
            Console.error("The default context cannot be deleted!");
            return;
        }

        ModelNode operation = new ModelNode();
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "jca");
        operation.get(ADDRESS).add("bootstrap-context", entity.getName());
        operation.get(OP).set(REMOVE);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to update JCA settings", response.getFailureDescription());
                else
                    Console.info("Success: Update JCA settings");

                loadWorkManager();
            }
        });
    }

    public void launchNewContextDialogue() {
        window = new ModalWindowLayout()
                .setTitle("New Bootstrap Context")
                .setWidget(new NewContextWizard(this, managers).asWidget())
                .build();
    }

    public void createNewContext(final JcaBootstrapContext entity) {
        closeDialoge();

        ModelNode operation = boostrapAdapter.fromEntity(entity);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "jca");
        operation.get(ADDRESS).add("bootstrap-context", entity.getName());
        operation.get(OP).set(ADD);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to add bootstrap context", response.getFailureDescription());
                else
                    Console.info("Success: Created bootstrap context");

                loadWorkManager();
            }
        });
    }

    public void closeDialoge() {
        window.hide();
    }

    public void launchNewManagerDialogue() {
        window = new ModalWindowLayout()
                .setTitle("New Work Manager Context")
                .setWidget(new NewManagerWizard(this).asWidget())
                .build();
    }

    public void onDeleteManager(final JcaWorkmanager entity) {
        if(entity.getName().equals("default"))
        {
            Console.error("The default work manager cannot be deleted!");
            return;
        }

        ModelNode operation = new ModelNode();
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "jca");
        operation.get(ADDRESS).add("workmanager", entity.getName());
        operation.get(OP).set(REMOVE);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to remove work manager", response.getFailureDescription());
                else
                    Console.info("Success: Removed work manager "+entity.getName());

                loadWorkManager();
            }
        });
    }

    public void createNewManager(final JcaWorkmanager entity) {
        closeDialoge();

        ModelNode operation = managerAdapter.fromEntity(entity);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "jca");
        operation.get(ADDRESS).add("workmanager", entity.getName());
        operation.get(OP).set(ADD);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                if(response.isFailure())
                    Console.error("Failed to add work manager", response.getFailureDescription());
                else
                    Console.info("Success: Created work manager");

                loadWorkManager();
            }
        });
    }
}