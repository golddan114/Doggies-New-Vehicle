package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Pin ingredient grammar and actually deserialize result fields from the selected SBW artifact. No world bootstrap. */
class NuclearRecipeContractTest {
    @Test void preparationRemoteHasACreativeEntryActualHandInteractionAndBothLanguagesWithoutASurvivalRecipe()throws Exception {
        String id="nuclear_preparation_remote";
        var model=json("/assets/doggiesnewvehicle/models/item/"+id+".json");
        assertEquals("minecraft:item/generated",model.get("parent").getAsString());
        assertEquals("minecraft:item/clock_00",model.getAsJsonObject("textures").get("layer0").getAsString());
        for(String locale:List.of("en_us","zh_cn")){
            var language=json("/assets/doggiesnewvehicle/lang/"+locale+".json");
            for(String prefix:List.of("item.","tooltip."))assertFalse(language.get(prefix+"doggiesnewvehicle."+id).getAsString().isBlank());
        }
        for(String folder:List.of("recipes","recipe"))assertNull(getClass().getResource("/data/doggiesnewvehicle/"+folder+"/"+id+".json"));
        for(String owner:List.of("DnvArtilleryItems","DnvArtilleryContent")){
            var node=compiled("artillery/"+owner);
            assertTrue(node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
                    .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).anyMatch(i->i.name.equals("NUCLEAR_PREPARATION_REMOTE")),owner);
        }
        var interact=compiled("artillery/silo/NuclearSiloEntity").methods.stream().filter(m->m.name.equals("interact")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(interact.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(i->i.name.equals("getItemInHand")),"The clicked hand selects the tool");
        assertTrue(Arrays.stream(interact.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(i->i.owner.endsWith("/NuclearPreparationRemoteItem")&&i.name.equals("useOnSilo")));
    }
    ClassNode compiled(String name)throws Exception{
        try(var input=getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/"+name+".class")){
            assertNotNull(input);var node=new ClassNode();new ClassReader(input.readAllBytes()).accept(node,0);return node;
        }
    }
    @Test void threeCoreGridsAndInventorySpritesMatchTheUserCraftingChainOnEitherPlatform()throws Exception {
        String[] names={"explosive_core","absorption_core","warhead_core"};
        String[][] expected={
            {"minecraft:tnt","minecraft:tnt","minecraft:tnt","minecraft:tnt","minecraft:fire_charge","minecraft:tnt","minecraft:tnt","minecraft:redstone_block","minecraft:tnt"},
            {"minecraft:sponge","minecraft:sponge","minecraft:sponge","minecraft:sponge","minecraft:bucket","minecraft:sponge","minecraft:bucket","minecraft:redstone_block","minecraft:bucket"},
            {"minecraft:netherite_ingot","minecraft:nether_star","minecraft:netherite_ingot","minecraft:dragon_breath","minecraft:netherite_block","minecraft:dragon_breath","doggiesnewvehicle:explosive_core","doggiesnewvehicle:absorption_core","doggiesnewvehicle:explosive_core"}
        };
        for(int i=0;i<names.length;i++){
            String name=names[i],base="/data/doggiesnewvehicle/";
            boolean neo=getClass().getResource(base+"recipe/"+name+".json")!=null;
            var recipe=json(base+(neo?"recipe/":"recipes/")+name+".json");
            assertEquals("minecraft:crafting_shaped",recipe.get("type").getAsString());
            var grid=new ArrayList<String>();
            for(var row:recipe.getAsJsonArray("pattern")){
                assertEquals(3,row.getAsString().length());
                for(char cell:row.getAsString().toCharArray())grid.add(recipe.getAsJsonObject("key").getAsJsonObject(String.valueOf(cell)).get("item").getAsString());
            }
            assertEquals(List.of(expected[i]),grid);var result=recipe.getAsJsonObject("result");
            assertEquals("doggiesnewvehicle:"+name,result.get(neo?"id":"item").getAsString());assertEquals(1,result.get("count").getAsInt());
            var model=json("/assets/doggiesnewvehicle/models/item/"+name+".json");
            assertEquals("minecraft:item/generated",model.get("parent").getAsString());
            assertEquals("doggiesnewvehicle:item/"+name,model.getAsJsonObject("textures").get("layer0").getAsString());
            try(var input=getClass().getResourceAsStream("/assets/doggiesnewvehicle/textures/item/"+name+".png")){
                assertNotNull(input);var image=javax.imageio.ImageIO.read(input);assertEquals(32,image.getWidth());assertEquals(32,image.getHeight());
                assertTrue(image.getColorModel().hasAlpha());assertEquals(0,image.getRGB(0,0)>>>24);
                int painted=0;for(int y=0;y<32;y++)for(int x=0;x<32;x++)if((image.getRGB(x,y)>>>24)>128)painted++;
                assertTrue(painted>300&&painted<900,"Visible centered sprite, actual transparent padding");
            }
            for(String locale:List.of("en_us","zh_cn"))assertFalse(json("/assets/doggiesnewvehicle/lang/"+locale+".json").get("item.doggiesnewvehicle."+name).getAsString().isBlank());
            var unlock=json(base+(neo?"advancement/":"advancements/")+"recipes/misc/"+name+".json");
            assertEquals("doggiesnewvehicle:"+name,unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
        }
    }
    JsonObject json(String resource)throws Exception {
        try(var input=getClass().getResourceAsStream(resource)){
            assertNotNull(input,resource);return JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void survivalRecipesDecodeIntoNativeItemResultsAndKeepExpensiveCounts() throws Exception {
        try(var zip=new ZipFile(SbwTestDependency.path().toFile())){
            var ingredient=node(zip,"com/atsuishio/superbwarfare/recipe/vehicle/VehicleAssemblingIngredient");
            String regex=ingredient.methods.stream().filter(m->m.name.equals("<clinit>"))
                    .flatMap(m->Arrays.stream(m.instructions.toArray())).filter(LdcInsnNode.class::isInstance)
                    .map(LdcInsnNode.class::cast).map(m->m.cst).filter(String.class::isInstance).map(String.class::cast)
                    .filter(s->s.contains("?<count>")).findFirst().orElseThrow();
            var grammar=Pattern.compile(regex);
            String source="com/atsuishio/superbwarfare/recipe/vehicle/VehicleAssemblingResult",target="com/liucodex/doggiesnewvehicle/compat/recipeprobe/Result";
            var result=node(zip,source);result.methods.removeIf(m->!m.name.equals("<init>"));result.innerClasses.clear();result.nestHostClass=null;result.nestMembers=null;
            var writer=new ClassWriter(0);result.accept(new ClassRemapper(writer,new Remapper(){
                @Override public String map(String n){return n.equals(source)?target:n.equals("net/minecraft/world/item/ItemStack")?"java/lang/Object":n;}
            }));
            var kind=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
            for(String name:List.of("nuclear_silo","nuclear_missile")){
                String resource="/data/doggiesnewvehicle/recipe/"+name+"_assembling.json";
                boolean neo=getClass().getResource(resource)!=null;
                if(!neo)resource=resource.replace("/recipe/","/recipes/");
                JsonObject recipe;
                try(var input=getClass().getResourceAsStream(resource)){
                    assertNotNull(input,"The selected platform must actually publish the recipe resource");
                    recipe=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
                assertEquals("superbwarfare:vehicle_assembling",recipe.get("type").getAsString());
                Object decoded=new Gson().fromJson(recipe.get("result"),kind);
                assertEquals("doggiesnewvehicle:"+name+(name.equals("nuclear_silo")?"_deployer":""),kind.getField("itemString").get(decoded));
                assertEquals("",kind.getField("entityTypeString").get(decoded),"Must yield deployer, not a box that bypasses the item journey");
                assertEquals(1,kind.getField("count").getInt(decoded));assertNull(kind.getField("nbt").get(decoded));
                Map<String,Integer> counts=new HashMap<>();
                for(var input:recipe.getAsJsonArray("inputs")){
                    var match=grammar.matcher(input.getAsString());assertTrue(match.matches(),input.toString());
                    int count=Integer.parseInt(match.group("count"));assertTrue(count>0);
                    assertNull(counts.put(match.group("id"),count));
                }
                assertEquals(name.equals("nuclear_silo")?128:16,counts.get((neo?"c:":"forge:")+"storage_blocks/steel"));
                assertEquals(name.equals("nuclear_silo")?32:16,counts.get("minecraft:netherite_ingot"));
                assertTrue(counts.get("superbwarfare:heavy_armament_module")>=4);
                if(name.equals("nuclear_missile"))assertEquals(1,counts.get("doggiesnewvehicle:warhead_core"));
                assertFalse(recipe.toString().contains("Energy"),"Assembly must not precharge a free launch");
            }
        }
    }
    static ClassNode node(ZipFile zip,String name) throws Exception {var n=new ClassNode();
        new ClassReader(zip.getInputStream(zip.getEntry(name+".class")).readAllBytes()).accept(n,0);return n;}
}
