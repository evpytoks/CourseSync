using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations;

[DbContext(typeof(AppDbContext))]
[Migration("20260325130000_AddCourseMaterials")]
public partial class AddCourseMaterials : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.CreateTable(
            name: "course_general_materials",
            columns: table => new
            {
                id = table.Column<Guid>(type: "uuid", nullable: false),
                course_id = table.Column<Guid>(type: "uuid", nullable: false),
                name = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false),
                author_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                author_email = table.Column<string>(type: "character varying(320)", maxLength: 320, nullable: false),
                storage_path = table.Column<string>(type: "character varying(512)", maxLength: 512, nullable: false),
                created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_course_general_materials", x => x.id);
                table.ForeignKey(
                    name: "FK_course_general_materials_courses_course_id",
                    column: x => x.course_id,
                    principalTable: "courses",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Cascade);
                table.ForeignKey(
                    name: "FK_course_general_materials_users_author_user_id",
                    column: x => x.author_user_id,
                    principalTable: "users",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Cascade);
            });

        migrationBuilder.CreateTable(
            name: "course_personal_materials",
            columns: table => new
            {
                id = table.Column<Guid>(type: "uuid", nullable: false),
                course_id = table.Column<Guid>(type: "uuid", nullable: false),
                name = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false),
                author_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                author_email = table.Column<string>(type: "character varying(320)", maxLength: 320, nullable: false),
                storage_path = table.Column<string>(type: "character varying(512)", maxLength: 512, nullable: false),
                created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_course_personal_materials", x => x.id);
                table.ForeignKey(
                    name: "FK_course_personal_materials_courses_course_id",
                    column: x => x.course_id,
                    principalTable: "courses",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Cascade);
                table.ForeignKey(
                    name: "FK_course_personal_materials_users_author_user_id",
                    column: x => x.author_user_id,
                    principalTable: "users",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Cascade);
            });

        migrationBuilder.CreateIndex(
            name: "IX_course_general_materials_course_id",
            table: "course_general_materials",
            column: "course_id");

        migrationBuilder.CreateIndex(
            name: "IX_course_general_materials_created_at",
            table: "course_general_materials",
            column: "created_at");

        migrationBuilder.CreateIndex(
            name: "IX_course_personal_materials_course_id",
            table: "course_personal_materials",
            column: "course_id");

        migrationBuilder.CreateIndex(
            name: "IX_course_personal_materials_created_at",
            table: "course_personal_materials",
            column: "created_at");
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable(name: "course_general_materials");
        migrationBuilder.DropTable(name: "course_personal_materials");
    }
}
